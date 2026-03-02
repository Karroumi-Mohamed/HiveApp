CREATE TABLE accounts (
    id          UUID                        NOT NULL,
    owner_id    UUID                        NOT NULL,
    name        VARCHAR(255)                NOT NULL,
    slug        VARCHAR(255)                NOT NULL,
    is_active   BOOLEAN                     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE    NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE    NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uq_accounts_owner_id UNIQUE (owner_id),
    CONSTRAINT uq_accounts_slug UNIQUE (slug)
);
