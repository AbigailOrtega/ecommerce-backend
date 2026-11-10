package com.ecommerce.service;

import com.ecommerce.entity.PostalCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads Mexico's postal code catalog from Correos de México. Their export page is an
 * ASP.NET form: load it, send its hidden fields back asking for the whole country as text, and
 * a zip with one pipe-separated file comes back (Latin-1, two header lines).
 */
@Slf4j
@Component
public class SepomexClient {

    static final String DEFAULT_URL = "https://www.correosdemexico.gob.mx/SSLServicios/ConsultaCP/CodigoPostal_Exportar.aspx";
    private static final Pattern HIDDEN_FIELD = Pattern.compile("<input type=\"hidden\" name=\"(__[A-Z]+)\" id=\"\\1\" value=\"([^\"]*)\"");
    private static final Charset LATIN_1 = StandardCharsets.ISO_8859_1;

    private final String url;
    private final HttpClient http;

    public SepomexClient(@Value("${app.postal-codes.source-url:" + DEFAULT_URL + "}") String url) {
        this.url = url;
        this.http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<PostalCode> download() throws IOException, InterruptedException {
        HttpResponse<byte[]> page = http.send(request(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (page.statusCode() != 200) throw new IOException("Correos de México export page answered " + page.statusCode());

        Map<String, String> form = hiddenFields(new String(page.body(), LATIN_1));
        if (!form.containsKey("__VIEWSTATE")) throw new IOException("Correos de México export page changed: no __VIEWSTATE");
        form.put("rblTipo", "txt");      // pipe-separated text
        form.put("cboEdo", "00");        // every state
        form.put("btnDescarga.x", "10"); // the download button is an image input
        form.put("btnDescarga.y", "10");

        HttpResponse<byte[]> export = http.send(request(url)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Referer", url)
                        .POST(HttpRequest.BodyPublishers.ofString(urlEncode(form)))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        String type = export.headers().firstValue("Content-Type").orElse("");
        if (export.statusCode() != 200 || type.startsWith("text/html")) {
            throw new IOException("Correos de México did not send the catalog (HTTP " + export.statusCode() + ", " + type + ")");
        }
        return parseZip(new ByteArrayInputStream(export.body()));
    }

    /** The settlements in the catalog zip; lines that do not parse are skipped. */
    static List<PostalCode> parseZip(InputStream zip) throws IOException {
        try (ZipInputStream in = new ZipInputStream(zip, LATIN_1)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".txt")) {
                    return parseText(in);
                }
            }
        }
        throw new IOException("The catalog zip has no .txt file");
    }

    /**
     * d_codigo|d_asenta|d_tipo_asenta|D_mnpio|d_estado|d_ciudad|… after a notice line and the
     * header line.
     */
    static List<PostalCode> parseText(InputStream text) throws IOException {
        List<PostalCode> rows = new ArrayList<>(170_000);
        // states, municipalities, cities and types repeat thousands of times: keep one copy each
        Map<String, String> shared = new java.util.HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(text, LATIN_1));
        String line;
        boolean header = true;
        while ((line = reader.readLine()) != null) {
            if (header) {
                header = !line.startsWith("d_codigo|");
                continue;
            }
            String[] f = line.split("\\|", -1);
            if (f.length < 6 || !f[0].matches("\\d{5}") || f[1].isBlank() || f[3].isBlank() || f[4].isBlank()) continue;
            rows.add(PostalCode.builder()
                    .code(f[0])
                    .settlement(f[1].trim())
                    .settlementType(share(shared, blankToNull(f[2])))
                    .municipality(share(shared, f[3].trim()))
                    .state(share(shared, f[4].trim()))
                    .city(share(shared, blankToNull(f[5])))
                    .build());
        }
        return rows;
    }

    static Map<String, String> hiddenFields(String html) {
        Map<String, String> fields = new LinkedHashMap<>();
        Matcher m = HIDDEN_FIELD.matcher(html);
        while (m.find()) fields.put(m.group(1), m.group(2).replace("&amp;", "&"));
        return fields;
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "Mozilla/5.0 (compatible; postal-code-catalog)");
    }

    private static String urlEncode(Map<String, String> form) {
        return form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String share(Map<String, String> shared, String s) {
        return s == null ? null : shared.computeIfAbsent(s, k -> k);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
