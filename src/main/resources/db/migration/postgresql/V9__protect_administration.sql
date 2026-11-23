ALTER TABLE ecofood.user_access ADD CONSTRAINT user_access_auth_fk FOREIGN KEY(user_id) REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE ecofood.user_access ENABLE ROW LEVEL SECURITY;
ALTER TABLE ecofood.admin_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE ecofood.catalog_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE ecofood.reporting_metadata ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON ecofood.user_access,ecofood.admin_audit,ecofood.catalog_events,ecofood.reporting_metadata FROM PUBLIC,anon,authenticated;
CREATE VIEW ecofood.admin_users AS SELECT id,created_at FROM auth.users WHERE deleted_at IS NULL;
REVOKE ALL ON ecofood.admin_users FROM PUBLIC,anon,authenticated;

-- Known creation times only; never invent past status transitions.
INSERT INTO ecofood.catalog_events SELECT gen_random_uuid(),id,'PRODUCT_CREATED',created_at FROM ecofood.products;
INSERT INTO ecofood.catalog_events SELECT gen_random_uuid(),id,'MARKET_CREATED',created_at FROM ecofood.markets;
CREATE FUNCTION ecofood.track_catalog_activity() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
 IF TG_TABLE_NAME='markets' THEN
   INSERT INTO ecofood.catalog_events VALUES(gen_random_uuid(),NEW.id,'MARKET_CREATED',NEW.created_at);
 ELSIF TG_OP='INSERT' THEN
   INSERT INTO ecofood.catalog_events VALUES(gen_random_uuid(),NEW.id,'PRODUCT_CREATED',NEW.created_at);
   INSERT INTO ecofood.catalog_events VALUES(gen_random_uuid(),NEW.id,'PRODUCT_SAVED',CURRENT_TIMESTAMP);
 ELSE
   IF OLD.is_active AND NOT NEW.is_active THEN
     INSERT INTO ecofood.catalog_events VALUES(gen_random_uuid(),NEW.id,'PRODUCT_DEACTIVATED',CURRENT_TIMESTAMP);
   END IF;
   IF ROW(OLD.name,OLD.category_id,OLD.manufacture_date,OLD.expiration_date,OLD.original_price,OLD.offer_price,OLD.stock_quantity)
      IS DISTINCT FROM ROW(NEW.name,NEW.category_id,NEW.manufacture_date,NEW.expiration_date,NEW.original_price,NEW.offer_price,NEW.stock_quantity) THEN
     INSERT INTO ecofood.catalog_events VALUES(gen_random_uuid(),NEW.id,'PRODUCT_SAVED',CURRENT_TIMESTAMP);
   END IF;
 END IF;
 RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION ecofood.track_catalog_activity() FROM PUBLIC,anon,authenticated;
CREATE TRIGGER ecofood_product_activity AFTER INSERT OR UPDATE ON ecofood.products FOR EACH ROW EXECUTE FUNCTION ecofood.track_catalog_activity();
CREATE TRIGGER ecofood_market_activity AFTER INSERT ON ecofood.markets FOR EACH ROW EXECUTE FUNCTION ecofood.track_catalog_activity();
