-- The colonia of a national shipping address, on its own: Skydropx wants it as area_level3 of the
-- quotation (it was getting the city) and street1 limited to street and number (45 characters).
-- Null for pickup orders and for orders placed before this column existed.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_neighborhood character varying(150);
