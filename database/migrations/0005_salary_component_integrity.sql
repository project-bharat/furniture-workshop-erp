BEGIN;
CREATE UNIQUE INDEX IF NOT EXISTS uq_employee_salary_component_effective
ON employee_salary_components(organization_id,employee_id,component_code,effective_from);
COMMIT;
