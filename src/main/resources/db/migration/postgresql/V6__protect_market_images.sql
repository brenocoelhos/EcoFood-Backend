ALTER TABLE ecofood.market_images ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON ecofood.market_images FROM PUBLIC, anon, authenticated;
