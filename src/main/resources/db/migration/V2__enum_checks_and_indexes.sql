-- Brings a database created by Hibernate's ddl-auto=update in line with the entities.
-- Safe to run on a database created from V1 too (everything is idempotent).

-- 1. Enum CHECK constraints: ddl-auto=update creates them once and never adds values added to
--    the enum later (e.g. SHIPMENT_PENDING), which makes saving such a row fail. Recreate them
--    from the current enums.

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'CONFIRMED'::character varying, 'PROCESSING'::character varying, 'SHIPPED'::character varying, 'DELIVERED'::character varying, 'CANCELLED'::character varying, 'REFUNDED'::character varying, 'SHIPMENT_PENDING'::character varying, 'DELIVERY_ATTEMPT'::character varying, 'DELIVERY_EXCEPTION'::character varying])::text[])));

ALTER TABLE pickup_availability DROP CONSTRAINT IF EXISTS pickup_availability_day_of_week_check;
ALTER TABLE pickup_availability ADD CONSTRAINT pickup_availability_day_of_week_check CHECK (((day_of_week)::text = ANY ((ARRAY['MONDAY'::character varying, 'TUESDAY'::character varying, 'WEDNESDAY'::character varying, 'THURSDAY'::character varying, 'FRIDAY'::character varying, 'SATURDAY'::character varying, 'SUNDAY'::character varying])::text[])));

ALTER TABLE pickup_availability DROP CONSTRAINT IF EXISTS pickup_availability_type_check;
ALTER TABLE pickup_availability ADD CONSTRAINT pickup_availability_type_check CHECK (((type)::text = ANY ((ARRAY['RECURRING'::character varying, 'SPECIFIC_DATE'::character varying])::text[])));

ALTER TABLE tickets DROP CONSTRAINT IF EXISTS tickets_status_check;
ALTER TABLE tickets ADD CONSTRAINT tickets_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'IN_PROGRESS'::character varying, 'RESOLVED'::character varying, 'CLOSED'::character varying])::text[])));

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (((role)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'ADMIN'::character varying])::text[])));


-- 2. Indexes declared on the entities (Order, OrderItem, CartItem, Review, PageView).

CREATE INDEX IF NOT EXISTS idx_cart_items_user_id ON cart_items USING btree (user_id);
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items USING btree (order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product_id ON order_items USING btree (product_id);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders USING btree (created_at);
CREATE INDEX IF NOT EXISTS idx_orders_guest_email ON orders USING btree (guest_email);
CREATE INDEX IF NOT EXISTS idx_orders_payment_id ON orders USING btree (payment_id);
CREATE INDEX IF NOT EXISTS idx_orders_status_created_at ON orders USING btree (status, created_at);
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders USING btree (user_id);
CREATE INDEX IF NOT EXISTS idx_page_views_path ON page_views USING btree (path);
CREATE INDEX IF NOT EXISTS idx_page_views_timestamp ON page_views USING btree ("timestamp");
CREATE INDEX IF NOT EXISTS idx_reviews_user_id ON reviews USING btree (user_id);
