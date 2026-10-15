-- The mobile app uses the Java API, never direct table access via PostgREST.
REVOKE ALL ON SCHEMA ecofood FROM PUBLIC, anon, authenticated;
REVOKE ALL ON ALL TABLES IN SCHEMA ecofood FROM PUBLIC, anon, authenticated;
ALTER TABLE ecofood.markets ADD CONSTRAINT markets_auth_user_fk
    FOREIGN KEY (owner_id) REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE ecofood.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE ecofood.markets ENABLE ROW LEVEL SECURITY;
ALTER TABLE ecofood.market_hours ENABLE ROW LEVEL SECURITY;
ALTER TABLE ecofood.products ENABLE ROW LEVEL SECURITY;
ALTER TABLE ecofood.product_images ENABLE ROW LEVEL SECURITY;
