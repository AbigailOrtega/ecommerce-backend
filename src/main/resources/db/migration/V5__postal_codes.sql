-- Mexico's postal code catalog (SEPOMEX, Correos de México), one row per settlement (colonia).
-- Filled and refreshed by the app itself (PostalCodeCatalogService), never shipped in the repo:
-- the catalog's terms forbid distributing it.
CREATE TABLE IF NOT EXISTS postal_codes (
    id bigserial PRIMARY KEY,
    code character varying(5) NOT NULL,
    settlement character varying(150) NOT NULL,
    settlement_type character varying(60),
    municipality character varying(120) NOT NULL,
    state character varying(60) NOT NULL,
    city character varying(120)
);
CREATE INDEX IF NOT EXISTS idx_postal_codes_code ON postal_codes (code);
