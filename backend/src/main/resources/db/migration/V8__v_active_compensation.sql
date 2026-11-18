-- Read-only projection owned by analytics: every salary record of every ACTIVE employee. The query
-- picks the record in force on a date it is given (the application clock), so nothing here calls CURDATE().
CREATE VIEW v_active_compensation AS
SELECT e.id AS id,
       e.employee_id AS employee_id,
       e.first_name AS first_name,
       e.last_name AS last_name,
       e.country AS country,
       e.department AS department,
       e.job_title AS job_title,
       c.amount AS amount,
       c.currency AS currency,
       c.effective_start AS effective_start,
       c.effective_end AS effective_end
FROM employee e
JOIN compensation_record c ON c.employee_id = e.id
WHERE e.status = 'ACTIVE';
