package com.acme.salary.compensation.application;

import com.acme.salary.audit.api.AuditEntityType;
import com.acme.salary.audit.api.AuditEvent;
import com.acme.salary.audit.api.AuditOperation;
import com.acme.salary.audit.api.AuditTrail;
import com.acme.salary.compensation.domain.RateChange;
import com.acme.salary.compensation.persistence.ExchangeRateEntity;
import com.acme.salary.compensation.persistence.ExchangeRateRepository;
import com.acme.salary.shared.domain.Actor;
import com.acme.salary.shared.domain.DomainException;
import com.acme.salary.shared.domain.ErrorKind;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists and edits exchange rates. An edit is one transaction that also writes its single audit entry. */
@Service
public class ExchangeRateEditService {

    private static final String USD = "USD";

    private final ExchangeRateRepository rates;
    private final AuditTrail audit;

    ExchangeRateEditService(ExchangeRateRepository rates, AuditTrail audit) {
        this.rates = rates;
        this.audit = audit;
    }

    /** One rate per currency, USD first and then alphabetically. */
    @Transactional(readOnly = true)
    public List<ExchangeRateView> list() {
        return rates.findAll().stream()
                .map(ExchangeRateEditService::view)
                .sorted(Comparator.comparing((ExchangeRateView v) -> !v.currency().equals(USD))
                        .thenComparing(ExchangeRateView::currency))
                .toList();
    }

    /** Saves a new rate. A rate equal to the current one changes nothing and is not audited. */
    @Transactional
    public ExchangeRateView update(Actor actor, String currencyCode, String rate, Long version) {
        RateChange change = RateChange.parse(currencyCode, rate, version);
        ExchangeRateEntity entity = rates.findById(change.currency().getCurrencyCode())
                .orElseThrow(() -> new DomainException(
                        ErrorKind.INVALID_INPUT, "UNSUPPORTED_CURRENCY", "There is no exchange rate for this currency"));
        if (entity.getVersion() != change.version()) {
            throw new DomainException(
                    ErrorKind.CONFLICT, "CONCURRENT_MODIFICATION", "The rate was changed by someone else; reload it and try again");
        }
        if (entity.getRateToUsd().compareTo(change.rate()) == 0) {
            return view(entity);
        }
        String previous = plain(entity.getRateToUsd());
        entity.changeRate(change.rate());
        rates.saveAndFlush(entity);
        audit.record(new AuditEvent(
                actor.userId(),
                actor.email(),
                AuditEntityType.EXCHANGE_RATE,
                idOf(entity.getCurrency()),
                null,
                AuditOperation.UPDATE,
                fields(entity.getCurrency(), previous),
                fields(entity.getCurrency(), plain(change.rate()))));
        return view(entity);
    }

    /** Rates are keyed by currency code; the audit trail wants an id, so each currency gets a stable one. */
    static UUID idOf(String currency) {
        return UUID.nameUUIDFromBytes(("exchange-rate:" + currency).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> fields(String currency, String rate) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("currency", currency);
        fields.put("rate", rate);
        return fields;
    }

    private static String plain(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString();
    }

    private static ExchangeRateView view(ExchangeRateEntity entity) {
        return new ExchangeRateView(
                entity.getCurrency(),
                entity.getRateToUsd().stripTrailingZeros(),
                entity.getVersion(),
                !entity.getCurrency().equals(USD));
    }
}
