CREATE TABLE ecofood.market_images (
    market_id UUID PRIMARY KEY REFERENCES ecofood.markets(id) ON DELETE CASCADE,
    version UUID NOT NULL,
    content_type VARCHAR(30) NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    content BYTEA NOT NULL,
    CHECK (octet_length(content) BETWEEN 1 AND 5242880)
);
