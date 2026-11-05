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
