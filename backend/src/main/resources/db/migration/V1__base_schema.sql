-- ════════════════════════════════════════════════════════════
-- HiveApp Platform — V1: Base Schema
-- Covers: Identity, Admin, Account, Company, Members,
--         Permissions, Roles, Plans, Collaboration, Billing
-- ════════════════════════════════════════════════════════════

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ──────────────────────────────────────────────────────────
-- SHARED IDENTITY LAYER
-- ──────────────────────────────────────────────────────────

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(30),
    avatar_url      VARCHAR(500),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_active ON users(is_active);

-- ──────────────────────────────────────────────────────────
-- MODULE & FEATURE REGISTRY (source of truth)
-- ──────────────────────────────────────────────────────────

CREATE TABLE modules (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code            VARCHAR(50) NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    icon            VARCHAR(100),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE features (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    module_id       UUID NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    code            VARCHAR(100) NOT NULL UNIQUE,
    name            VARCHAR(150) NOT NULL,
    description     TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_features_module ON features(module_id);

-- ──────────────────────────────────────────────────────────
-- PERMISSIONS (defined by features — global, not per-account)
-- ──────────────────────────────────────────────────────────

CREATE TABLE permissions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feature_id      UUID NOT NULL REFERENCES features(id) ON DELETE CASCADE,
    code            VARCHAR(150) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    action          VARCHAR(50) NOT NULL,
    resource        VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_permissions_feature ON permissions(feature_id);
CREATE INDEX idx_permissions_code ON permissions(code);
CREATE INDEX idx_permissions_action_resource ON permissions(action, resource);

-- ──────────────────────────────────────────────────────────
-- ADMIN PLATFORM
-- ──────────────────────────────────────────────────────────

CREATE TABLE admin_users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    is_super_admin  BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE admin_roles (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE admin_permissions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code            VARCHAR(150) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    module_id       UUID REFERENCES modules(id) ON DELETE SET NULL,
    action          VARCHAR(50) NOT NULL,
    resource        VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_permissions_code ON admin_permissions(code);

CREATE TABLE admin_role_permissions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    admin_role_id       UUID NOT NULL REFERENCES admin_roles(id) ON DELETE CASCADE,
    admin_permission_id UUID NOT NULL REFERENCES admin_permissions(id) ON DELETE CASCADE,
    granted_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (admin_role_id, admin_permission_id)
);

CREATE TABLE admin_user_roles (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    admin_user_id   UUID NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    admin_role_id   UUID NOT NULL REFERENCES admin_roles(id) ON DELETE CASCADE,
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (admin_user_id, admin_role_id)
);

-- ──────────────────────────────────────────────────────────
-- PLANS
-- ──────────────────────────────────────────────────────────

CREATE TABLE plans (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code            VARCHAR(50) NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    price           DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    billing_cycle   VARCHAR(20) NOT NULL DEFAULT 'monthly',
    max_companies   INTEGER NOT NULL DEFAULT 1,
    max_members     INTEGER NOT NULL DEFAULT 5,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE plan_features (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_id         UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    feature_id      UUID NOT NULL REFERENCES features(id) ON DELETE CASCADE,
    config          JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (plan_id, feature_id)
);

CREATE INDEX idx_plan_features_plan ON plan_features(plan_id);

-- ──────────────────────────────────────────────────────────
-- ACCOUNTS
-- ──────────────────────────────────────────────────────────

CREATE TABLE accounts (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id        UUID NOT NULL UNIQUE REFERENCES users(id),
    plan_id         UUID NOT NULL REFERENCES plans(id),
    name            VARCHAR(150) NOT NULL,
    slug            VARCHAR(100) NOT NULL UNIQUE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_slug ON accounts(slug);
CREATE INDEX idx_accounts_owner ON accounts(owner_id);
CREATE INDEX idx_accounts_plan ON accounts(plan_id);

-- ──────────────────────────────────────────────────────────
-- SUBSCRIPTIONS (Billing)
-- ──────────────────────────────────────────────────────────

CREATE TABLE subscriptions (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id              UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    plan_id                 UUID NOT NULL REFERENCES plans(id),
    status                  VARCHAR(20) NOT NULL DEFAULT 'active',
    current_period_start    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    current_period_end      TIMESTAMPTZ NOT NULL,
    cancelled_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_account ON subscriptions(account_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);

-- ──────────────────────────────────────────────────────────
-- COMPANIES
-- ──────────────────────────────────────────────────────────

CREATE TABLE companies (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id      UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    legal_name      VARCHAR(300),
    tax_id          VARCHAR(50),
    industry        VARCHAR(100),
    country         VARCHAR(5),
    address         TEXT,
    logo_url        VARCHAR(500),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_companies_account ON companies(account_id);

CREATE TABLE company_modules (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id      UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    module_id       UUID NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    activated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deactivated_at  TIMESTAMPTZ,
    UNIQUE (company_id, module_id)
);

CREATE INDEX idx_company_modules_company ON company_modules(company_id);

-- ──────────────────────────────────────────────────────────
-- MEMBERS
-- ──────────────────────────────────────────────────────────

CREATE TABLE members (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id),
    account_id      UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    display_name    VARCHAR(150) NOT NULL,
    is_owner        BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, account_id)
);

CREATE INDEX idx_members_account ON members(account_id);
CREATE INDEX idx_members_user ON members(user_id);

-- ──────────────────────────────────────────────────────────
-- ROLES (per account, dynamic)
-- ──────────────────────────────────────────────────────────

CREATE TABLE roles (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id      UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    is_system_role  BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_roles_account ON roles(account_id);

CREATE TABLE role_permissions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_id         UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id   UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);

-- ──────────────────────────────────────────────────────────
-- MEMBER ROLE ASSIGNMENTS (with optional company scope)
-- ──────────────────────────────────────────────────────────

CREATE TABLE member_roles (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    member_id       UUID NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    role_id         UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    company_id      UUID REFERENCES companies(id) ON DELETE CASCADE,
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (member_id, role_id, company_id)
);

CREATE INDEX idx_member_roles_member ON member_roles(member_id);
CREATE INDEX idx_member_roles_company ON member_roles(company_id);

-- ──────────────────────────────────────────────────────────
-- COLLABORATION
-- ──────────────────────────────────────────────────────────

CREATE TABLE collaborations (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    client_account_id   UUID NOT NULL REFERENCES accounts(id),
    provider_account_id UUID NOT NULL REFERENCES accounts(id),
    company_id          UUID NOT NULL REFERENCES companies(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'pending',
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at         TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_different_accounts CHECK (client_account_id != provider_account_id)
);

CREATE INDEX idx_collaborations_client ON collaborations(client_account_id);
CREATE INDEX idx_collaborations_provider ON collaborations(provider_account_id);
CREATE INDEX idx_collaborations_status ON collaborations(status);

CREATE TABLE collaboration_permissions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    collaboration_id    UUID NOT NULL REFERENCES collaborations(id) ON DELETE CASCADE,
    permission_id       UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    granted_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (collaboration_id, permission_id)
);

CREATE INDEX idx_collab_permissions_collab ON collaboration_permissions(collaboration_id);
