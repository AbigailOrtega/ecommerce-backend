-- Indexes declared on the entities (Order, OrderItem, CartItem, Review).
-- With DDL_AUTO=update Hibernate creates them on the next deploy; with validate/none run this once.
-- CONCURRENTLY avoids locking the tables while the store is live (run each statement outside a transaction).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_user_id            ON orders (user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_status_created_at  ON orders (status, created_at);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_created_at         ON orders (created_at);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_payment_id         ON orders (payment_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_guest_email        ON orders (guest_email);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_order_items_order_id      ON order_items (order_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_order_items_product_id    ON order_items (product_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cart_items_user_id        ON cart_items (user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reviews_user_id           ON reviews (user_id);
