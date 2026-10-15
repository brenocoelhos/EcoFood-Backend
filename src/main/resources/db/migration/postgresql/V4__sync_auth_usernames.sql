ALTER TABLE ecofood.user_logins ADD CONSTRAINT user_logins_auth_user_fk
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE ecofood.user_logins ADD CONSTRAINT username_format
    CHECK (username ~ '^[a-z0-9_.]{3,30}$');
ALTER TABLE ecofood.user_logins ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON ecofood.user_logins FROM PUBLIC, anon, authenticated;

-- Ambiguous or invalid legacy handles remain email-only; never pick an arbitrary owner.
INSERT INTO ecofood.user_logins (user_id, username, email)
SELECT id,
       CASE WHEN handle ~ '^[a-z0-9_.]{3,30}$' AND count(*) OVER (PARTITION BY handle) = 1
            THEN handle ELSE NULL END,
       email
FROM (
    SELECT id, lower(btrim(raw_user_meta_data ->> 'username')) AS handle, email
    FROM auth.users WHERE deleted_at IS NULL
) existing_users;

CREATE FUNCTION ecofood.sync_auth_username() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    handle text;
    username_changed boolean;
BEGIN
    IF NEW.deleted_at IS NOT NULL THEN
        DELETE FROM ecofood.user_logins WHERE user_id = NEW.id;
        RETURN NEW;
    END IF;

    handle := nullif(lower(btrim(NEW.raw_user_meta_data ->> 'username')), '');
    IF TG_OP = 'INSERT' THEN
        username_changed := true;
    ELSE
        username_changed := (NEW.raw_user_meta_data ->> 'username') IS DISTINCT FROM
                            (OLD.raw_user_meta_data ->> 'username');
    END IF;

    IF username_changed AND handle IS NOT NULL AND handle !~ '^[a-z0-9_.]{3,30}$' THEN
        RAISE EXCEPTION 'Invalid username format' USING ERRCODE = '23514';
    END IF;

    IF username_changed THEN
        INSERT INTO ecofood.user_logins (user_id, username, email)
        VALUES (NEW.id, handle, NEW.email)
        ON CONFLICT (user_id) DO UPDATE SET username = EXCLUDED.username, email = EXCLUDED.email;
    ELSE
        UPDATE ecofood.user_logins SET email = NEW.email WHERE user_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION ecofood.sync_auth_username() FROM PUBLIC, anon, authenticated;
CREATE TRIGGER ecofood_sync_auth_username
AFTER INSERT OR UPDATE OF raw_user_meta_data, email, deleted_at ON auth.users
FOR EACH ROW EXECUTE FUNCTION ecofood.sync_auth_username();
