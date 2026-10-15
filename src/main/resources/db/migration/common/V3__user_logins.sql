CREATE TABLE ecofood.user_logins (
    user_id UUID PRIMARY KEY,
    username VARCHAR(30) UNIQUE,
    email VARCHAR(320),
    CONSTRAINT username_lowercase CHECK (username = LOWER(username))
);
