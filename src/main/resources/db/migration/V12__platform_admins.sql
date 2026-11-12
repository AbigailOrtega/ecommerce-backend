-- The platform's super admins are not users of any store (store #1 included): their own table,
-- their own login (on PLATFORM_HOST). An account is added by hand with its e-mail only; its owner
-- sets the password with "¿Olvidaste tu contraseña?" on the platform page:
--   INSERT INTO platform_admins (email) VALUES ('someone@example.com');
CREATE TABLE platform_admins (
    id                 bigserial    PRIMARY KEY,
    email              varchar(255) NOT NULL,
    password           varchar(255),
    reset_token        varchar(255),
    reset_token_expiry timestamp(6),
    created_at         timestamp(6) NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX platform_admins_email_lower_key ON platform_admins (lower(email));

-- V11 let store users be SUPER_ADMIN; that role is gone (nobody had it yet, but just in case)
UPDATE users SET role = 'ADMIN' WHERE role = 'SUPER_ADMIN';
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
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('CUSTOMER', 'ADMIN'));
