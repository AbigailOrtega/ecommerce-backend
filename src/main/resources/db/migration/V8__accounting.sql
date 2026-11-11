-- Accounting: when an order was paid and refunded, what the payment provider and the shipping
-- label cost, and what each garment cost the store.

-- Sales are counted by the day the money came in, not the day the order was created (an order
-- can wait unpaid); refunds by the day they happened.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS paid_at timestamp(6) without time zone;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS refunded_at timestamp(6) without time zone;
-- Stripe / PayPal commission for the payment (read from the provider after the payment)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_fee numeric(10,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_fee_attempts integer DEFAULT 0 NOT NULL;
-- what Skydropx charged for the label
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_label_cost numeric(10,2);
-- what the store paid for the product (optional), and the cost at the time of each sale
ALTER TABLE products ADD COLUMN IF NOT EXISTS cost_price numeric(10,2);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS unit_cost numeric(10,2);

-- Orders from before: anything that got past "pending" was paid; the creation day is the best
-- date we have. A refunded order's last change is its refund.
UPDATE orders SET paid_at = created_at WHERE paid_at IS NULL AND status NOT IN ('PENDING', 'CANCELLED');
UPDATE orders SET refunded_at = updated_at WHERE refunded_at IS NULL AND status = 'REFUNDED';

CREATE INDEX IF NOT EXISTS idx_orders_paid_at ON orders (paid_at);
CREATE INDEX IF NOT EXISTS idx_orders_refunded_at ON orders (refunded_at);
