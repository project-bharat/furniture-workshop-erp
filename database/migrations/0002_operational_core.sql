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
