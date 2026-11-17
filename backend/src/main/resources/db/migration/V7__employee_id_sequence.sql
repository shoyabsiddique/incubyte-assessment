-- Hands out Employee IDs (EMP-000001, ...). The single row is locked while an ID is allocated, so two
-- people added at the same moment can not receive the same number. Works on MySQL as well as MariaDB.
CREATE TABLE employee_id_sequence (
    id         TINYINT NOT NULL,
    last_value INT     NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO employee_id_sequence (id, last_value) VALUES (1, 0);
