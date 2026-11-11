-- A category can be hidden from the store (its menu, filters and product labels) without deleting
-- it: the admin still sees and uses it, and its products stay on sale.
ALTER TABLE categories ADD COLUMN IF NOT EXISTS visible boolean DEFAULT true NOT NULL;
