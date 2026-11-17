-- Append-only audit trail. Rows are inserted in the same transaction as the change they describe and
-- are never updated or deleted by the application. The actor's email is copied in so the trail stays
-- readable without reaching into the auth tables. before_json / after_json hold only allowlisted fields.
CREATE TABLE audit_entry (
    id                  CHAR(36)     NOT NULL,
    user_id             CHAR(36)     NOT NULL,
    user_email          VARCHAR(254) NOT NULL,
    entity_type         VARCHAR(30)  NOT NULL,
    entity_id           CHAR(36)     NOT NULL,
    subject_employee_id CHAR(36)     NULL,
    operation           VARCHAR(10)  NOT NULL,
    before_json         LONGTEXT     NULL,
    after_json          LONGTEXT     NULL,
    created_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_entry_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT chk_audit_entity_type CHECK (entity_type IN ('EMPLOYEE', 'COMPENSATION_RECORD', 'EXCHANGE_RATE')),
    CONSTRAINT chk_audit_operation CHECK (operation IN ('CREATE', 'UPDATE', 'REVISE'))
);

CREATE INDEX idx_audit_subject_time ON audit_entry (subject_employee_id, created_at);
CREATE INDEX idx_audit_time ON audit_entry (created_at);
CREATE INDEX idx_audit_user ON audit_entry (user_id);
CREATE INDEX idx_audit_entity_type ON audit_entry (entity_type);
