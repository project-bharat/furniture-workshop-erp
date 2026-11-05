-- Furniture Workshop ERP — Neon PostgreSQL initial setup
-- IMPORTANT: Run this file exactly once on a NEW, EMPTY Neon database.
-- It combines the repository's ordered migrations 0001 through 0005.
-- For an existing database, do not rerun this file; apply only new migrations.


-- ===== BEGIN 0001_foundation.sql =====
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
    UNIQUE (id, organization_id),
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

CREATE UNIQUE INDEX uq_roles_global_key ON roles(role_key) WHERE organization_id IS NULL;

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
    membership_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    PRIMARY KEY (membership_id, branch_id),
    FOREIGN KEY (membership_id, organization_id)
        REFERENCES organization_memberships(id, organization_id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, branch_id)
        REFERENCES branches(organization_id, id)
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


-- Built-in organization role templates. Each organization membership may be assigned
-- these system roles; custom organization-specific roles can be added in a later migration.
INSERT INTO roles (organization_id, role_key, display_name, description, is_system_role) VALUES
(NULL, 'OWNER_ADMIN', 'Owner / Admin', 'Full business administration and approval authority', TRUE),
(NULL, 'WORKSHOP_MANAGER', 'Workshop Manager', 'Manage projects, production stages and workshop tasks', TRUE),
(NULL, 'CARPENTER_WORKER', 'Carpenter / Worker', 'View assigned work and update assigned tasks', TRUE),
(NULL, 'OFFICE_SALES', 'Office / Sales Staff', 'Manage customer enquiries and project coordination', TRUE),
(NULL, 'DELIVERY_INSTALLATION', 'Delivery / Installation Team', 'View assigned deliveries and installation work', TRUE),
(NULL, 'PURCHASE_INVENTORY', 'Purchase / Inventory Manager', 'Manage materials, stock and purchasing workflows', TRUE)
ON CONFLICT (role_key) WHERE organization_id IS NULL DO NOTHING;

INSERT INTO role_permissions (role_id, permission_key)
SELECT r.id, p.permission_key
FROM roles r CROSS JOIN permissions p
WHERE r.organization_id IS NULL AND r.role_key = 'OWNER_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_key)
SELECT r.id, x.permission_key
FROM (VALUES
    ('WORKSHOP_MANAGER','dashboard.view'),
    ('WORKSHOP_MANAGER','customers.view'),
    ('WORKSHOP_MANAGER','projects.view'),
    ('WORKSHOP_MANAGER','projects.manage'),
    ('WORKSHOP_MANAGER','production.view'),
    ('WORKSHOP_MANAGER','production.manage'),
    ('WORKSHOP_MANAGER','tasks.update_assigned'),
    ('WORKSHOP_MANAGER','inventory.view'),
    ('CARPENTER_WORKER','dashboard.view'),
    ('CARPENTER_WORKER','projects.view'),
    ('CARPENTER_WORKER','production.view'),
    ('CARPENTER_WORKER','tasks.update_assigned'),
    ('OFFICE_SALES','dashboard.view'),
    ('OFFICE_SALES','customers.view'),
    ('OFFICE_SALES','customers.manage'),
    ('OFFICE_SALES','projects.view'),
    ('OFFICE_SALES','projects.manage'),
    ('DELIVERY_INSTALLATION','dashboard.view'),
    ('DELIVERY_INSTALLATION','projects.view'),
    ('DELIVERY_INSTALLATION','production.view'),
    ('DELIVERY_INSTALLATION','tasks.update_assigned'),
    ('PURCHASE_INVENTORY','dashboard.view'),
    ('PURCHASE_INVENTORY','projects.view'),
    ('PURCHASE_INVENTORY','inventory.view'),
    ('PURCHASE_INVENTORY','inventory.manage'),
    ('PURCHASE_INVENTORY','purchasing.manage')
) AS x(role_key, permission_key)
JOIN roles r ON r.role_key = x.role_key AND r.organization_id IS NULL
ON CONFLICT DO NOTHING;

COMMIT;
-- ===== END 0001_foundation.sql =====


-- ===== BEGIN 0002_operational_core.sql =====
-- Furniture Workshop ERP — operational core
-- Adds customer, project, production, inventory, procurement and approval records.
-- Applied migrations are immutable; use a new migration for future changes.
BEGIN;

CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    branch_id UUID,
    customer_code VARCHAR(40),
    customer_type VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL'
        CHECK (customer_type IN ('INDIVIDUAL','BUSINESS')),
    display_name VARCHAR(180) NOT NULL,
    phone VARCHAR(30),
    alternate_phone VARCHAR(30),
    email VARCHAR(254),
    gstin VARCHAR(15),
    billing_address JSONB NOT NULL DEFAULT '{}'::jsonb,
    site_address JSONB NOT NULL DEFAULT '{}'::jsonb,
    notes TEXT,
    created_by UUID REFERENCES users(id),
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, customer_code),
    FOREIGN KEY (organization_id, branch_id) REFERENCES branches(organization_id, id)
);
CREATE INDEX idx_customers_org_name ON customers(organization_id, lower(display_name))
    WHERE archived_at IS NULL;
CREATE INDEX idx_customers_org_phone ON customers(organization_id, phone)
    WHERE archived_at IS NULL;

CREATE TABLE enquiries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    branch_id UUID,
    customer_id UUID NOT NULL,
    enquiry_number VARCHAR(40) NOT NULL,
    source VARCHAR(30) NOT NULL DEFAULT 'WALK_IN'
        CHECK (source IN ('WALK_IN','PHONE','WHATSAPP','WEBSITE','REFERRAL','OTHER')),
    title VARCHAR(180) NOT NULL,
    description TEXT,
    requirement JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(24) NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW','CONTACTED','SITE_VISIT','QUOTATION_PENDING','QUOTED','WON','LOST','ON_HOLD')),
    priority VARCHAR(12) NOT NULL DEFAULT 'NORMAL'
        CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    expected_value NUMERIC(14,2) CHECK (expected_value IS NULL OR expected_value >= 0),
    next_follow_up_at TIMESTAMPTZ,
    assigned_to UUID REFERENCES users(id),
    converted_project_id UUID,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, enquiry_number),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, branch_id) REFERENCES branches(organization_id, id),
    FOREIGN KEY (organization_id, customer_id) REFERENCES customers(organization_id, id)
);
CREATE INDEX idx_enquiries_pipeline ON enquiries(organization_id, status, updated_at DESC);
CREATE INDEX idx_enquiries_followup ON enquiries(organization_id, next_follow_up_at)
    WHERE status NOT IN ('WON','LOST');

CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    branch_id UUID,
    customer_id UUID NOT NULL,
    enquiry_id UUID,
    project_number VARCHAR(40) NOT NULL,
    title VARCHAR(180) NOT NULL,
    description TEXT,
    project_type VARCHAR(30) NOT NULL DEFAULT 'CUSTOM_FURNITURE'
        CHECK (project_type IN ('CUSTOM_FURNITURE','INTERIOR','RENOVATION','MIXED','OTHER')),
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','QUOTATION','APPROVED','PLANNED','IN_PRODUCTION','READY_FOR_DELIVERY','INSTALLATION','COMPLETED','ON_HOLD','CANCELLED')),
    priority VARCHAR(12) NOT NULL DEFAULT 'NORMAL'
        CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    site_address JSONB NOT NULL DEFAULT '{}'::jsonb,
    start_date DATE,
    target_completion_date DATE,
    actual_completion_date DATE,
    estimated_total NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (estimated_total >= 0),
    approved_total NUMERIC(14,2) CHECK (approved_total IS NULL OR approved_total >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    project_manager_id UUID REFERENCES users(id),
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, project_number),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, branch_id) REFERENCES branches(organization_id, id),
    FOREIGN KEY (organization_id, customer_id) REFERENCES customers(organization_id, id),
    FOREIGN KEY (organization_id, enquiry_id) REFERENCES enquiries(organization_id, id),
    CHECK (target_completion_date IS NULL OR start_date IS NULL OR target_completion_date >= start_date)
);
CREATE INDEX idx_projects_org_status ON projects(organization_id, status, target_completion_date);
CREATE INDEX idx_projects_customer ON projects(organization_id, customer_id, created_at DESC);

ALTER TABLE enquiries
    ADD CONSTRAINT fk_enquiries_converted_project
    FOREIGN KEY (organization_id, converted_project_id) REFERENCES projects(organization_id, id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE project_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    project_id UUID NOT NULL,
    item_number INTEGER NOT NULL,
    name VARCHAR(180) NOT NULL,
    description TEXT,
    room_or_area VARCHAR(120),
    quantity NUMERIC(12,3) NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit VARCHAR(20) NOT NULL DEFAULT 'PCS',
    dimensions_mm JSONB NOT NULL DEFAULT '{}'::jsonb,
    customer_dimensions JSONB NOT NULL DEFAULT '{}'::jsonb,
    material_specification JSONB NOT NULL DEFAULT '{}'::jsonb,
    estimated_material_cost NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (estimated_material_cost >= 0),
    estimated_labour_cost NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (estimated_labour_cost >= 0),
    estimated_other_cost NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (estimated_other_cost >= 0),
    selling_price NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (selling_price >= 0),
    status VARCHAR(24) NOT NULL DEFAULT 'DESIGN'
        CHECK (status IN ('DESIGN','APPROVED','CUTTING','ASSEMBLY','FINISHING','QC','READY','INSTALLED','CANCELLED')),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, project_id, item_number),
    FOREIGN KEY (organization_id, project_id) REFERENCES projects(organization_id, id) ON DELETE CASCADE
);
CREATE INDEX idx_project_items_project ON project_items(organization_id, project_id, sort_order);

CREATE TABLE project_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    project_id UUID NOT NULL,
    project_item_id UUID,
    title VARCHAR(180) NOT NULL,
    instructions TEXT,
    stage VARCHAR(24) NOT NULL
        CHECK (stage IN ('SITE_MEASUREMENT','DESIGN','CUTTING','CARPENTRY','ASSEMBLY','POLISHING','UPHOLSTERY','QUALITY_CHECK','PACKING','DELIVERY','INSTALLATION','OTHER')),
    status VARCHAR(20) NOT NULL DEFAULT 'TODO'
        CHECK (status IN ('TODO','ASSIGNED','IN_PROGRESS','BLOCKED','DONE','CANCELLED')),
    assigned_to UUID REFERENCES users(id),
    planned_start DATE,
    due_date DATE,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    estimated_minutes INTEGER CHECK (estimated_minutes IS NULL OR estimated_minutes >= 0),
    actual_minutes INTEGER CHECK (actual_minutes IS NULL OR actual_minutes >= 0),
    completion_note TEXT,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, project_id) REFERENCES projects(organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, project_item_id) REFERENCES project_items(organization_id, id)
);
CREATE INDEX idx_tasks_assignee_status ON project_tasks(organization_id, assigned_to, status, due_date);
CREATE INDEX idx_tasks_project_stage ON project_tasks(organization_id, project_id, stage, status);

CREATE TABLE materials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    sku VARCHAR(60),
    name VARCHAR(180) NOT NULL,
    category VARCHAR(80),
    description TEXT,
    base_unit VARCHAR(20) NOT NULL,
    reorder_level NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
    default_unit_cost NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (default_unit_cost >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, sku)
);
CREATE INDEX idx_materials_org_name ON materials(organization_id, lower(name)) WHERE is_active;

CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    branch_id UUID,
    material_id UUID NOT NULL,
    movement_type VARCHAR(24) NOT NULL
        CHECK (movement_type IN ('OPENING','PURCHASE_RECEIPT','PROJECT_ISSUE','RETURN','ADJUSTMENT_IN','ADJUSTMENT_OUT','TRANSFER_IN','TRANSFER_OUT')),
    quantity_delta NUMERIC(14,3) NOT NULL CHECK (quantity_delta <> 0),
    unit_cost NUMERIC(14,2) CHECK (unit_cost IS NULL OR unit_cost >= 0),
    reference_type VARCHAR(40),
    reference_id UUID,
    notes TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, branch_id) REFERENCES branches(organization_id, id),
    FOREIGN KEY (organization_id, material_id) REFERENCES materials(organization_id, id)
);
CREATE INDEX idx_inventory_ledger ON inventory_movements(organization_id, material_id, occurred_at DESC);
CREATE INDEX idx_inventory_branch_time ON inventory_movements(organization_id, branch_id, occurred_at DESC);

CREATE TABLE purchase_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    branch_id UUID,
    request_number VARCHAR(40) NOT NULL,
    project_id UUID,
    supplier_name VARCHAR(180),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','ORDERED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
    required_by DATE,
    subtotal NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
    tax_total NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (tax_total >= 0),
    total NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
    notes TEXT,
    requested_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, request_number),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, branch_id) REFERENCES branches(organization_id, id),
    FOREIGN KEY (organization_id, project_id) REFERENCES projects(organization_id, id)
);
CREATE INDEX idx_purchase_requests_status ON purchase_requests(organization_id, status, created_at DESC);

CREATE TABLE purchase_request_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    purchase_request_id UUID NOT NULL,
    material_id UUID,
    description VARCHAR(240) NOT NULL,
    quantity NUMERIC(14,3) NOT NULL CHECK (quantity > 0),
    unit VARCHAR(20) NOT NULL,
    estimated_unit_cost NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (estimated_unit_cost >= 0),
    line_total NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (line_total >= 0),
    FOREIGN KEY (organization_id, purchase_request_id) REFERENCES purchase_requests(organization_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, material_id) REFERENCES materials(organization_id, id)
);

CREATE TABLE approval_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    branch_id UUID,
    request_type VARCHAR(40) NOT NULL
        CHECK (request_type IN ('QUOTATION','PURCHASE','EXPENSE','DISCOUNT','STOCK_ADJUSTMENT','OTHER')),
    entity_type VARCHAR(60) NOT NULL,
    entity_id UUID NOT NULL,
    requested_by UUID NOT NULL REFERENCES users(id),
    requested_amount NUMERIC(14,2) CHECK (requested_amount IS NULL OR requested_amount >= 0),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','APPROVED','REJECTED','WITHDRAWN')),
    decision_by UUID REFERENCES users(id),
    decision_at TIMESTAMPTZ,
    decision_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, branch_id) REFERENCES branches(organization_id, id),
    CHECK (
      (status = 'PENDING' AND decision_by IS NULL AND decision_at IS NULL)
      OR (status <> 'PENDING' AND decision_at IS NOT NULL)
    )
);
CREATE INDEX idx_approval_queue ON approval_requests(organization_id, status, created_at)
    WHERE status = 'PENDING';

INSERT INTO permissions(permission_key, module_key, description) VALUES
('enquiries.view','customers','View sales enquiries and follow-ups'),
('enquiries.manage','customers','Create and update sales enquiries'),
('tasks.assign','production','Assign workshop tasks to workers'),
('inventory.issue','inventory','Issue and return materials against projects'),
('approvals.request','approvals','Submit a business action for owner approval')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_id, permission_key)
SELECT r.id, x.permission_key
FROM (VALUES
    ('WORKSHOP_MANAGER','enquiries.view'),
    ('WORKSHOP_MANAGER','tasks.assign'),
    ('OFFICE_SALES','enquiries.view'),
    ('OFFICE_SALES','enquiries.manage'),
    ('PURCHASE_INVENTORY','approvals.request')
) AS x(role_key, permission_key)
JOIN roles r ON r.role_key = x.role_key AND r.organization_id IS NULL
ON CONFLICT DO NOTHING;

COMMIT;
-- ===== END 0002_operational_core.sql =====


-- ===== BEGIN 0003_workforce_payroll.sql =====
-- Furniture Workshop ERP — workforce, attendance and payroll
-- Immutable payroll snapshots and payment ledger; advances are recovered through allocations.
BEGIN;

CREATE TABLE employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    branch_id UUID,
    employee_code VARCHAR(40),
    full_name VARCHAR(180) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(254),
    role_title VARCHAR(100) NOT NULL,
    employment_type VARCHAR(20) NOT NULL DEFAULT 'FULL_TIME'
        CHECK (employment_type IN ('FULL_TIME','PART_TIME','CONTRACT','DAILY_WAGE')),
    employment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (employment_status IN ('ACTIVE','ON_LEAVE','EXITED')),
    joining_date DATE,
    exit_date DATE,
    monthly_salary NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (monthly_salary >= 0),
    pay_basis VARCHAR(20) NOT NULL DEFAULT 'MONTHLY'
        CHECK (pay_basis IN ('MONTHLY','DAILY','HOURLY','PIECE_RATE')),
    weekly_off_day SMALLINT NOT NULL DEFAULT 0 CHECK (weekly_off_day BETWEEN 0 AND 6),
    notes TEXT,
    created_by UUID REFERENCES users(id),
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, employee_code),
    FOREIGN KEY (organization_id, branch_id) REFERENCES branches(organization_id, id),
    CHECK (exit_date IS NULL OR joining_date IS NULL OR exit_date >= joining_date)
);
CREATE INDEX idx_employees_org_active ON employees(organization_id, employment_status, lower(full_name))
    WHERE archived_at IS NULL;

CREATE TABLE employee_salary_components (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    employee_id UUID NOT NULL,
    component_code VARCHAR(40) NOT NULL,
    component_name VARCHAR(100) NOT NULL,
    component_type VARCHAR(16) NOT NULL CHECK (component_type IN ('EARNING','DEDUCTION')),
    calculation_type VARCHAR(16) NOT NULL DEFAULT 'FIXED'
        CHECK (calculation_type IN ('FIXED','PERCENTAGE')),
    amount NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, employee_id) REFERENCES employees(organization_id, id),
    CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX idx_salary_components_employee ON employee_salary_components(organization_id, employee_id, is_active);

CREATE TABLE attendance_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    branch_id UUID,
    employee_id UUID NOT NULL,
    attendance_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PRESENT','ABSENT','HALF_DAY','PAID_LEAVE','UNPAID_LEAVE','HOLIDAY','WEEKLY_OFF')),
    check_in_at TIMESTAMPTZ,
    check_out_at TIMESTAMPTZ,
    regular_hours NUMERIC(6,2) NOT NULL DEFAULT 0 CHECK (regular_hours >= 0),
    overtime_hours NUMERIC(6,2) NOT NULL DEFAULT 0 CHECK (overtime_hours >= 0),
    note TEXT,
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL','DEVICE','IMPORT','SYSTEM')),
    recorded_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, employee_id, attendance_date),
    FOREIGN KEY (organization_id, employee_id) REFERENCES employees(organization_id, id),
    FOREIGN KEY (organization_id, branch_id) REFERENCES branches(organization_id, id),
    CHECK (check_out_at IS NULL OR check_in_at IS NULL OR check_out_at >= check_in_at)
);
CREATE INDEX idx_attendance_org_date ON attendance_entries(organization_id, attendance_date DESC);
CREATE INDEX idx_attendance_employee_date ON attendance_entries(organization_id, employee_id, attendance_date DESC);

CREATE TABLE payroll_adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    employee_id UUID NOT NULL,
    adjustment_type VARCHAR(16) NOT NULL CHECK (adjustment_type IN ('ADVANCE','DEDUCTION','INCENTIVE','BONUS')),
    adjustment_date DATE NOT NULL DEFAULT CURRENT_DATE,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    recovered_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (recovered_amount >= 0),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','PARTIALLY_SETTLED','SETTLED','VOID')),
    reason TEXT NOT NULL,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, employee_id) REFERENCES employees(organization_id, id),
    CHECK (recovered_amount <= amount)
);
CREATE INDEX idx_payroll_adjustments_open ON payroll_adjustments(organization_id, employee_id, adjustment_date)
    WHERE status IN ('OPEN','PARTIALLY_SETTLED');

CREATE TABLE payroll_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    branch_id UUID,
    payroll_month DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','REVIEW','APPROVED','PARTIALLY_PAID','PAID','VOID')),
    employee_count INTEGER NOT NULL DEFAULT 0 CHECK (employee_count >= 0),
    gross_total NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (gross_total >= 0),
    deduction_total NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (deduction_total >= 0),
    net_total NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (net_total >= 0),
    prepared_by UUID REFERENCES users(id),
    approved_by UUID REFERENCES users(id),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, branch_id, payroll_month),
    FOREIGN KEY (organization_id, branch_id) REFERENCES branches(organization_id, id),
    CHECK (payroll_month = date_trunc('month', payroll_month)::date)
);
CREATE INDEX idx_payroll_runs_org_month ON payroll_runs(organization_id, payroll_month DESC);

CREATE TABLE payroll_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    payroll_run_id UUID NOT NULL REFERENCES payroll_runs(id),
    employee_id UUID NOT NULL,
    employee_name_snapshot VARCHAR(180) NOT NULL,
    role_snapshot VARCHAR(100) NOT NULL,
    monthly_salary_snapshot NUMERIC(14,2) NOT NULL CHECK (monthly_salary_snapshot >= 0),
    working_days NUMERIC(6,2) NOT NULL DEFAULT 0 CHECK (working_days >= 0),
    present_days NUMERIC(6,2) NOT NULL DEFAULT 0 CHECK (present_days >= 0),
    half_days NUMERIC(6,2) NOT NULL DEFAULT 0 CHECK (half_days >= 0),
    paid_leave_days NUMERIC(6,2) NOT NULL DEFAULT 0 CHECK (paid_leave_days >= 0),
    unpaid_days NUMERIC(6,2) NOT NULL DEFAULT 0 CHECK (unpaid_days >= 0),
    overtime_hours NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (overtime_hours >= 0),
    overtime_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (overtime_amount >= 0),
    earnings_breakdown JSONB NOT NULL DEFAULT '[]'::jsonb,
    deductions_breakdown JSONB NOT NULL DEFAULT '[]'::jsonb,
    gross_pay NUMERIC(14,2) NOT NULL CHECK (gross_pay >= 0),
    advance_recovery NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (advance_recovery >= 0),
    net_pay NUMERIC(14,2) NOT NULL CHECK (net_pay >= 0),
    paid_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, payroll_run_id, employee_id),
    FOREIGN KEY (organization_id, employee_id) REFERENCES employees(organization_id, id),
    CHECK (paid_amount <= net_pay)
);
CREATE INDEX idx_payroll_items_employee ON payroll_items(organization_id, employee_id, created_at DESC);

CREATE TABLE payroll_adjustment_recoveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    adjustment_id UUID NOT NULL REFERENCES payroll_adjustments(id),
    payroll_item_id UUID NOT NULL REFERENCES payroll_items(id),
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    recovered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (adjustment_id, payroll_item_id)
);

CREATE TABLE payroll_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    payroll_item_id UUID NOT NULL REFERENCES payroll_items(id),
    employee_id UUID NOT NULL,
    payment_date DATE NOT NULL DEFAULT CURRENT_DATE,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    payment_method VARCHAR(16) NOT NULL CHECK (payment_method IN ('CASH','BANK_TRANSFER','UPI','CHEQUE','OTHER')),
    reference_number VARCHAR(100),
    note TEXT,
    idempotency_key UUID NOT NULL DEFAULT gen_random_uuid(),
    recorded_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, employee_id) REFERENCES employees(organization_id, id),
    UNIQUE (organization_id, idempotency_key)
);
CREATE INDEX idx_payroll_payments_item_date ON payroll_payments(organization_id, payroll_item_id, payment_date DESC);
CREATE INDEX idx_payroll_payments_employee_date ON payroll_payments(organization_id, employee_id, payment_date DESC);

COMMIT;
-- ===== END 0003_workforce_payroll.sql =====


-- ===== BEGIN 0004_workforce_permissions.sql =====
BEGIN;
INSERT INTO permissions(permission_key,module_key,description) VALUES
('workforce.view','workforce','View employee records and workforce summaries'),
('workforce.manage','workforce','Create and update employee records'),
('attendance.view','workforce','View employee attendance records'),
('attendance.manage','workforce','Record and correct employee attendance'),
('payroll.view','payroll','View payroll runs and employee payment history'),
('payroll.manage','payroll','Prepare draft payroll runs and payroll adjustments'),
('payroll.approve','payroll','Approve and lock payroll runs'),
('payroll.pay','payroll','Record salary payments')
ON CONFLICT DO NOTHING;
INSERT INTO role_permissions(role_id,permission_key)
SELECT r.id,x.permission_key FROM (VALUES
('WORKSHOP_MANAGER','workforce.view'),('WORKSHOP_MANAGER','attendance.view'),('WORKSHOP_MANAGER','attendance.manage')
) x(role_key,permission_key) JOIN roles r ON r.role_key=x.role_key AND r.organization_id IS NULL
ON CONFLICT DO NOTHING;
INSERT INTO role_permissions(role_id,permission_key)
SELECT r.id,p.permission_key FROM roles r CROSS JOIN permissions p
WHERE r.organization_id IS NULL AND r.role_key='OWNER_ADMIN'
AND p.permission_key IN ('workforce.view','workforce.manage','attendance.view','attendance.manage','payroll.view','payroll.manage','payroll.approve','payroll.pay')
ON CONFLICT DO NOTHING;
COMMIT;
-- ===== END 0004_workforce_permissions.sql =====


-- ===== BEGIN 0005_salary_component_integrity.sql =====
BEGIN;
CREATE UNIQUE INDEX IF NOT EXISTS uq_employee_salary_component_effective
ON employee_salary_components(organization_id,employee_id,component_code,effective_from);
COMMIT;
-- ===== END 0005_salary_component_integrity.sql =====
