package com.acme.salary.compensation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "exchange_rate")
public class ExchangeRateEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "rate_to_usd", nullable = false, precision = 19, scale = 8)
    private BigDecimal rateToUsd;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ExchangeRateEntity() {}

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getRateToUsd() {
        return rateToUsd;
    }

    public long getVersion() {
        return version;
    }
}
