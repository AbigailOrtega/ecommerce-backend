package com.ecommerce.service;

import com.ecommerce.entity.PostalCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SepomexClient")
class SepomexClientTest {

    /** The shape of Correos de México's export: a notice, the header, then pipe-separated rows (Latin-1). */
    private static final String SAMPLE = String.join("\r\n",
            "El Catálogo Nacional de Códigos Postales, es elaborado por Correos de México…",
            "d_codigo|d_asenta|d_tipo_asenta|D_mnpio|d_estado|d_ciudad|d_CP|c_estado|c_oficina|c_CP|c_tipo_asenta|c_mnpio|id_asenta_cpcons|d_zona|c_cve_ciudad",
            "06700|Roma Norte|Colonia|Cuauhtémoc|Ciudad de México|Ciudad de México|06001|09|06001||09|015|0001|Urbano|03",
            "56530|San Mateo Huitzilzingo|Pueblo|Chalco|México||56601|15|56601||28|025|0013|Rural|",
            "garbage line",
            "1234|Too short code|Colonia|X|Y|Z|||||||||",
            "");

    /** A catalog zip with the notice and header lines, then {@code rows}. */
    static byte[] catalogZip(List<String> rows) throws Exception {
        return zip("CPdescarga.txt", "Aviso\r\nd_codigo|d_asenta|d_tipo_asenta|D_mnpio|d_estado|d_ciudad|d_CP\r\n"
                + String.join("\r\n", rows));
    }

    static byte[] zip(String name, String content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.ISO_8859_1));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    @DisplayName("reads settlements from the catalog zip: accents (Latin-1), empty city, bad lines skipped")
    void parsesZip() throws Exception {
        List<PostalCode> rows = SepomexClient.parseZip(new ByteArrayInputStream(zip("CPdescarga.txt", SAMPLE)));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).satisfies(r -> {
            assertThat(r.getCode()).isEqualTo("06700");
            assertThat(r.getSettlement()).isEqualTo("Roma Norte");
            assertThat(r.getMunicipality()).isEqualTo("Cuauhtémoc");
            assertThat(r.getState()).isEqualTo("Ciudad de México");
            assertThat(r.getCity()).isEqualTo("Ciudad de México");
        });
        assertThat(rows.get(1).getCity()).isNull();
        assertThat(rows.get(1).getSettlementType()).isEqualTo("Pueblo");
    }

    @Test
    @DisplayName("finds the ASP.NET hidden fields the export form needs")
    void hiddenFields() {
        String html = "<input type=\"hidden\" name=\"__VIEWSTATE\" id=\"__VIEWSTATE\" value=\"abc&amp;def\" />"
                + "<input type=\"hidden\" name=\"__EVENTVALIDATION\" id=\"__EVENTVALIDATION\" value=\"xyz\" />";
        Map<String, String> fields = SepomexClient.hiddenFields(html);
        assertThat(fields).containsEntry("__VIEWSTATE", "abc&def").containsEntry("__EVENTVALIDATION", "xyz");
    }

    /** Against the real site: ./mvnw test -Dtest=SepomexClientTest -Dsepomex.live=true */
    @Test
    @EnabledIfSystemProperty(named = "sepomex.live", matches = "true")
    @DisplayName("(live) downloads the whole catalog from Correos de México")
    void liveDownload() throws Exception {
        byte[] zip = new SepomexClient(SepomexClient.DEFAULT_URL).downloadZip();
        List<PostalCode> rows = SepomexClient.parseZip(new ByteArrayInputStream(zip));
        assertThat(rows.size()).isGreaterThan(PostalCodeCatalogService.MIN_ROWS);
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.getCode()).isEqualTo("06700");
            assertThat(r.getSettlement()).isEqualTo("Roma Norte");
        });
    }
}
