-- The analytics view now carries the salary record id so each row has a stable identity for JPA.
CREATE OR REPLACE VIEW v_active_compensation AS
SELECT c.id AS compensation_id,
       e.id AS id,
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
