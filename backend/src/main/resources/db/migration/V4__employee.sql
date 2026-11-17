-- Employee profiles. Salaries live in compensation_record, so a pay change never rewrites a profile.
-- The explicit case-insensitive collation makes work email uniqueness ignore case on any engine.
CREATE TABLE employee (
    id              CHAR(36)     NOT NULL,
    employee_id     VARCHAR(20)  NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    work_email      VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    job_title       VARCHAR(100) NOT NULL,
    department      VARCHAR(100) NOT NULL,
    country         VARCHAR(30)  NOT NULL,
    city            VARCHAR(100) NOT NULL,
    employment_type VARCHAR(20)  NOT NULL,
    hire_date       DATE         NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_employee_employee_id UNIQUE (employee_id),
    CONSTRAINT uq_employee_work_email UNIQUE (work_email),
    CONSTRAINT chk_employee_status CHECK (status IN ('ACTIVE', 'ON_LEAVE', 'TERMINATED')),
    CONSTRAINT chk_employee_type CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT')),
    CONSTRAINT chk_employee_country CHECK (country IN
        ('INDIA', 'USA', 'UK', 'GERMANY', 'SINGAPORE', 'UAE', 'CANADA', 'AUSTRALIA'))
);

CREATE INDEX idx_employee_name ON employee (last_name, first_name);
CREATE INDEX idx_employee_peer_group ON employee (country, department, job_title);
CREATE INDEX idx_employee_department ON employee (department);
CREATE INDEX idx_employee_job_title ON employee (job_title);
CREATE INDEX idx_employee_status ON employee (status);
