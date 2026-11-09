-- Where a visit came from: the utm_source of the link, or the referring site (google.com,
-- instagram.com...). Sent with the first page of each visit; null for direct visits and for
-- page views recorded before this column existed.
ALTER TABLE page_views ADD COLUMN IF NOT EXISTS source character varying(50);
