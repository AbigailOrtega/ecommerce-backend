-- Each store's own Stripe, PayPal and Skydropx accounts (secrets encrypted with ENCRYPTION_KEY).
CREATE TABLE store_credentials (
    id                           bigserial    PRIMARY KEY,
    tenant_id                    bigint       NOT NULL REFERENCES tenants (id),
    stripe_public_key            varchar(255),
    stripe_secret_key_enc        varchar(1024),
    stripe_webhook_secret_enc    varchar(1024),
    paypal_client_id             varchar(255),
    paypal_client_secret_enc     varchar(1024),
    paypal_live                  boolean      NOT NULL DEFAULT false,
    paypal_webhook_id            varchar(255),
    skydropx_client_id           varchar(255),
    skydropx_client_secret_enc   varchar(1024),
    skydropx_sandbox             boolean      NOT NULL DEFAULT true,
    skydropx_webhook_secret_enc  varchar(1024),
    updated_at                   timestamp(6)
);
CREATE UNIQUE INDEX store_credentials_tenant_key ON store_credentials (tenant_id);
