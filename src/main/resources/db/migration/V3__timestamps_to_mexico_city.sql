-- The app used to run with the JVM's default zone, UTC on Render, so every LocalDateTime.now()
-- (created_at, updated_at, page view time, reset token expiry) was stored as UTC wall-clock time.
-- From this version the JVM runs in America/Mexico_City (EcommerceApplication), so existing values
-- are converted once to Mexico City wall-clock time. All of these columns are filled with "now",
-- never with a time a person typed, so all of them are converted.

UPDATE cart_items SET
    created_at = (created_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City';

UPDATE categories SET
    created_at = (created_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City';

UPDATE coupons SET
    created_at = (created_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City';

UPDATE orders SET
    created_at = (created_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City',
    updated_at = (updated_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City';

UPDATE page_views SET
    "timestamp" = ("timestamp" AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City';

UPDATE pickup_exceptions SET
    created_at = (created_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City';

UPDATE products SET
    created_at = (created_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City',
    updated_at = (updated_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City';

UPDATE promo_banners SET
    created_at = (created_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City';

UPDATE reviews SET
    created_at = (created_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City',
    updated_at = (updated_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City';

UPDATE tickets SET
    created_at = (created_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City',
    updated_at = (updated_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City';

UPDATE users SET
    created_at = (created_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City',
    reset_token_expiry = (reset_token_expiry AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City',
    updated_at = (updated_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Mexico_City';
