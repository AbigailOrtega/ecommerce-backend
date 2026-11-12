package com.ecommerce.credentials;

import com.ecommerce.service.PayPalClient;
import com.ecommerce.service.SkydropxService;
import com.stripe.exception.StripeException;
import com.stripe.model.Balance;
import com.stripe.model.WebhookEndpoint;
import com.stripe.net.RequestOptions;
import com.stripe.param.WebhookEndpointCreateParams;
import com.stripe.param.WebhookEndpointListParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/** The calls that check a store's keys with Stripe, PayPal and Skydropx, and set up their webhooks. */
@Component
@RequiredArgsConstructor
public class ProviderChecks {

    private final PayPalClient paypal;
    private final SkydropxService skydropx;

    /** Null when the key works; otherwise what is wrong, for the admin. */
    public String checkStripe(String secretKey) {
        try {
            Balance.retrieve(RequestOptions.builder().setApiKey(secretKey).build());
            return null;
        } catch (StripeException e) {
            return "Stripe no aceptó la clave secreta: " + e.getStripeError().getMessage();
        } catch (RuntimeException e) {
            return "No se pudo comprobar la clave con Stripe";
        }
    }

    /** Creates the store's webhook (replacing one to the same URL) and returns its signing secret. */
    public String createStripeWebhook(String secretKey, String url) throws StripeException {
        RequestOptions opts = RequestOptions.builder().setApiKey(secretKey).build();
        for (WebhookEndpoint existing : WebhookEndpoint.list(WebhookEndpointListParams.builder().setLimit(100L).build(), opts).getData()) {
            if (url.equalsIgnoreCase(existing.getUrl())) existing.delete(opts);
        }
        WebhookEndpointCreateParams.Builder params = WebhookEndpointCreateParams.builder().setUrl(url)
                .setDescription("Tienda: pagos y reembolsos");
        params.addEnabledEvent(WebhookEndpointCreateParams.EnabledEvent.PAYMENT_INTENT__SUCCEEDED)
                .addEnabledEvent(WebhookEndpointCreateParams.EnabledEvent.PAYMENT_INTENT__PAYMENT_FAILED)
                .addEnabledEvent(WebhookEndpointCreateParams.EnabledEvent.PAYMENT_INTENT__CANCELED)
                .addEnabledEvent(WebhookEndpointCreateParams.EnabledEvent.CHARGE__REFUNDED);
        return WebhookEndpoint.create(params.build(), opts).getSecret();
    }

    public boolean checkPayPal(String clientId, String clientSecret, boolean live) {
        return paypal.credentialsWork(clientId, clientSecret, live);
    }

    public String ensurePayPalWebhook(StoreKeys.PayPal account, String url) throws PayPalClient.PayPalApiException {
        return paypal.ensureWebhook(account, url);
    }

    public boolean checkSkydropx(String clientId, String clientSecret, boolean sandbox) {
        return skydropx.credentialsWork(clientId, clientSecret, sandbox);
    }
}
