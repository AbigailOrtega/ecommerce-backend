-- A third role: SUPER_ADMIN runs the platform (every store). The role check is found by what it
-- checks, not by name (a schema built by Hibernate names its constraints its own way).
DO $$
DECLARE c record;
BEGIN
    FOR c IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace ns ON ns.oid = rel.relnamespace AND ns.nspname = current_schema()
        WHERE con.contype = 'c' AND rel.relname = 'users'
          AND pg_get_constraintdef(con.oid) ILIKE '%role%'
    LOOP
        EXECUTE format('ALTER TABLE users DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('CUSTOMER', 'ADMIN', 'SUPER_ADMIN'));
