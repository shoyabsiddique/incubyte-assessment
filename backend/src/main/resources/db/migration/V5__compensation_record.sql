-- One row per salary period. A salary change closes the latest row and adds a new one; amounts are
-- never overwritten. The open (current) row is the one with no end date.
CREATE TABLE compensation_record (
    id              CHAR(36)      NOT NULL,
    employee_id     CHAR(36)      NOT NULL,
    amount          DECIMAL(19,4) NOT NULL,
    currency        CHAR(3)       NOT NULL,
    effective_start DATE          NOT NULL,
    effective_end   DATE          NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_compensation_employee_start UNIQUE (employee_id, effective_start),
    CONSTRAINT chk_compensation_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_compensation_range CHECK (effective_end IS NULL OR effective_end >= effective_start),
    CONSTRAINT fk_compensation_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_compensation_currency FOREIGN KEY (currency) REFERENCES exchange_rate (currency)
);

CREATE INDEX idx_compensation_current ON compensation_record (effective_end, employee_id);
