-- Furniture Workshop ERP — foundation schema
-- PostgreSQL 15+. Applied migrations are immutable; use a new migration for changes.
BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_name VARCHAR(200) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    gstin VARCHAR(15),
    default_currency CHAR(3) NOT NULL DEFAULT 'INR',
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(120) NOT NULL,
    code VARCHAR(30),
    address JSONB NOT NULL DEFAULT '{}'::jsonb,
    phone VARCHAR(30),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, code)
);
CREATE INDEX idx_branches_org_active ON branches(organization_id, is_active);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254),
    phone VARCHAR(30),
    full_name VARCHAR(160) NOT NULL,
    password_hash TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (email IS NOT NULL OR phone IS NOT NULL)
);
CREATE UNIQUE INDEX uq_users_email_ci ON users (lower(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX uq_users_phone ON users(phone) WHERE phone IS NOT NULL;

CREATE TABLE organization_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id UUID NOT NULL REFERENCES users(id),
    default_branch_id UUID,
    membership_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (membership_status IN ('INVITED','ACTIVE','SUSPENDED','REMOVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, user_id),
    FOREIGN KEY (organization_id, default_branch_id)
        REFERENCES branches(organization_id, id)
);

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES organizations(id),
    role_key VARCHAR(60) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description TEXT,
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, role_key)
);

CREATE TABLE permissions (
    permission_key VARCHAR(120) PRIMARY KEY,
    module_key VARCHAR(60) NOT NULL,
    description TEXT NOT NULL
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_key VARCHAR(120) NOT NULL REFERENCES permissions(permission_key),
    PRIMARY KEY (role_id, permission_key)
);

CREATE TABLE membership_roles (
    membership_id UUID NOT NULL REFERENCES organization_memberships(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (membership_id, role_id)
);

CREATE TABLE membership_branches (
    membership_id UUID NOT NULL REFERENCES organization_memberships(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branches(id),
    PRIMARY KEY (membership_id, branch_id)
);

CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    token_hash TEXT NOT NULL UNIQUE,
    device_label VARCHAR(120),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_session_id UUID REFERENCES refresh_sessions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ
);
CREATE INDEX idx_refresh_sessions_user_active
    ON refresh_sessions(user_id, expires_at) WHERE revoked_at IS NULL;

CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id UUID NOT NULL REFERENCES users(id),
    platform VARCHAR(20) NOT NULL CHECK (platform IN ('ANDROID','WEB')),
    device_name VARCHAR(120),
    app_version VARCHAR(40),
    last_seen_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_devices_org_user ON devices(organization_id, user_id);

CREATE TABLE audit_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    branch_id UUID REFERENCES branches(id),
    actor_user_id UUID REFERENCES users(id),
    action_key VARCHAR(120) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID,
    request_id VARCHAR(100),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_org_time ON audit_logs(organization_id, occurred_at DESC);
CREATE INDEX idx_audit_entity ON audit_logs(organization_id, entity_type, entity_id);

CREATE TABLE sync_event_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    device_id UUID NOT NULL REFERENCES devices(id),
    event_id UUID NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED'
        CHECK (processing_status IN ('RECEIVED','APPLIED','REJECTED','CONFLICT')),
    response JSONB,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    UNIQUE (device_id, event_id),
    UNIQUE (organization_id, idempotency_key)
);
CREATE INDEX idx_sync_receipts_org_received
    ON sync_event_receipts(organization_id, received_at);

INSERT INTO permissions(permission_key, module_key, description) VALUES
('dashboard.view','dashboard','View operational dashboard'),
('users.view','identity','View organization users'),
('users.manage','identity','Invite, update and deactivate users'),
('roles.manage','identity','Manage role assignments and permissions'),
('customers.view','customers','View customer and enquiry records'),
('customers.manage','customers','Create and update customers and enquiries'),
('projects.view','projects','View assigned and permitted projects'),
('projects.manage','projects','Create and manage projects'),
('production.view','production','View production schedules and tasks'),
('production.manage','production','Manage production stages and assignments'),
('tasks.update_assigned','production','Update tasks assigned to the current worker'),
('inventory.view','inventory','View materials and stock'),
('inventory.manage','inventory','Manage inventory and stock movements'),
('purchasing.manage','procurement','Create and manage purchase requests and orders'),
('finance.view','finance','View financial information'),
('finance.manage','finance','Manage payments, expenses and invoices'),
('approvals.decide','approvals','Approve or reject controlled business actions'),
('reports.view','reports','View permitted operational and financial reports'),
('audit.view','audit','View organization audit history')
ON CONFLICT DO NOTHING;

COMMIT;
