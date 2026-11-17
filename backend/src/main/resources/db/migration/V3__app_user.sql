-- HR Manager accounts. Passwords are stored only as BCrypt hashes. Accounts are created by the
-- seed runner or the application, never by a migration, so no credential lives in version control.
-- The explicit case-insensitive collation makes email uniqueness ignore case on any engine.
CREATE TABLE app_user (
    id            CHAR(36)     NOT NULL,
    email         VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);
