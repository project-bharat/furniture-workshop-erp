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
