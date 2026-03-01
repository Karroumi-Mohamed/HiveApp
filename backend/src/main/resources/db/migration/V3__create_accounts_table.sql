CREATE TABLE accounts (
    id              UUID                        NOT NULL,
    user_id         UUID                        NOT NULL,
    account_name    VARCHAR(255)                NOT NULL,
    status          VARCHAR(20)                 NOT NULL DEFAULT 'ACTIVE',
    account_type    VARCHAR(20)                 NOT NULL DEFAULT 'PERSONAL',
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE    NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uq_accounts_user_id UNIQUE (user_id)
);
