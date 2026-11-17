-- One row per salary period. A salary change closes the latest row and adds a new one; amounts are
-- never overwritten. The open row (no end date) is the salary in force until it is closed; people
-- who have left keep their last row open, and analytics only look at active employees.
-- open_marker is 1 only on an open row, so the unique key below allows at most one open row per
-- employee (NULLs never collide).
CREATE TABLE compensation_record (
    id              CHAR(36)      NOT NULL,
    employee_id     CHAR(36)      NOT NULL,
    amount          DECIMAL(19,4) NOT NULL,
    currency        CHAR(3)       NOT NULL,
    effective_start DATE          NOT NULL,
    effective_end   DATE          NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    open_marker     TINYINT AS (IF(effective_end IS NULL, 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    CONSTRAINT uq_compensation_one_open_record UNIQUE (employee_id, open_marker),
    CONSTRAINT uq_compensation_employee_start UNIQUE (employee_id, effective_start),
    CONSTRAINT chk_compensation_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_compensation_range CHECK (effective_end IS NULL OR effective_end >= effective_start),
    CONSTRAINT fk_compensation_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_compensation_currency FOREIGN KEY (currency) REFERENCES exchange_rate (currency)
);

CREATE INDEX idx_compensation_current ON compensation_record (effective_end, employee_id);
