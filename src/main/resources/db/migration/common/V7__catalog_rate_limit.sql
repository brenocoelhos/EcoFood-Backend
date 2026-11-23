ALTER TABLE ecofood.markets ADD COLUMN catalog_window TIMESTAMP WITH TIME ZONE;
ALTER TABLE ecofood.markets ADD COLUMN catalog_requests INTEGER NOT NULL DEFAULT 0;
