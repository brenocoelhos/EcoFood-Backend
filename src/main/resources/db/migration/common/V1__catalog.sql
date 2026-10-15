CREATE TABLE ecofood.categories (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);
INSERT INTO ecofood.categories (id, name) VALUES
('frutas-legumes', 'Frutas & Legumes'), ('carnes-aves', 'Carnes & Aves'),
('laticinios', 'Queijos & Laticínios'), ('secos-enlatados', 'Secos & Enlatados'), ('outros', 'Outros');

CREATE TABLE ecofood.markets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL UNIQUE,
    market_name VARCHAR(160) NOT NULL,
    market_phone VARCHAR(25) NOT NULL,
    document VARCHAR(18) NOT NULL UNIQUE,
    postal_code VARCHAR(9) NOT NULL,
    state VARCHAR(2) NOT NULL,
    city VARCHAR(100) NOT NULL,
    neighborhood VARCHAR(100) NOT NULL,
    address VARCHAR(200) NOT NULL,
    address_number VARCHAR(20) NOT NULL,
    complement VARCHAR(100) NOT NULL DEFAULT '',
    name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    username VARCHAR(80) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(25) NOT NULL,
    referral_source VARCHAR(200) NOT NULL DEFAULT '',
    hours_configured BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE ecofood.market_hours (
    id UUID PRIMARY KEY,
    market_id UUID NOT NULL REFERENCES ecofood.markets(id) ON DELETE CASCADE,
    day_of_week INTEGER NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    opens_at TIME NOT NULL,
    closes_at TIME NOT NULL,
    CHECK (opens_at <> closes_at),
    UNIQUE (market_id, day_of_week, opens_at)
);
CREATE INDEX market_hours_market_idx ON ecofood.market_hours(market_id);

CREATE TABLE ecofood.products (
    id UUID PRIMARY KEY,
    market_id UUID NOT NULL REFERENCES ecofood.markets(id) ON DELETE CASCADE,
    category_id VARCHAR(50) NOT NULL REFERENCES ecofood.categories(id),
    name VARCHAR(160) NOT NULL,
    search_name VARCHAR(200) NOT NULL,
    manufacture_date DATE,
    expiration_date DATE NOT NULL,
    original_price NUMERIC(10,2) NOT NULL CHECK (original_price > 0),
    offer_price NUMERIC(10,2) NOT NULL CHECK (offer_price > 0 AND offer_price < original_price),
    stock_quantity INTEGER NOT NULL CHECK (stock_quantity >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (manufacture_date IS NULL OR manufacture_date <= expiration_date)
);
CREATE INDEX products_market_idx ON ecofood.products(market_id);
CREATE INDEX products_catalog_idx ON ecofood.products(is_active, expiration_date, category_id);
CREATE TABLE ecofood.product_images (
    product_id UUID PRIMARY KEY REFERENCES ecofood.products(id) ON DELETE CASCADE,
    content_type VARCHAR(30) NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    content BYTEA NOT NULL,
    CHECK (octet_length(content) <= 5242880)
);
