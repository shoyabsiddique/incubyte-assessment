-- Exchange rates to USD, one row per supported currency. The rates below are illustrative
-- demo values, editable later through the application. USD is always exactly 1.
CREATE TABLE exchange_rate (
    currency    CHAR(3)       NOT NULL,
    rate_to_usd DECIMAL(19,8) NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (currency),
    CONSTRAINT chk_exchange_rate_positive CHECK (rate_to_usd > 0),
    CONSTRAINT chk_exchange_rate_usd_is_one CHECK (currency <> 'USD' OR rate_to_usd = 1)
);

INSERT INTO exchange_rate (currency, rate_to_usd) VALUES
    ('USD', 1.00000000),
    ('INR', 0.01200000),
    ('GBP', 1.27000000),
    ('EUR', 1.08000000),
    ('SGD', 0.74000000),
    ('AED', 0.27230000),
    ('CAD', 0.73000000),
    ('AUD', 0.65000000);
