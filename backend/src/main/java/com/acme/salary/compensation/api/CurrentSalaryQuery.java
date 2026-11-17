package com.acme.salary.compensation.api;

import com.acme.salary.shared.domain.ExchangeRates;
import com.acme.salary.shared.domain.Money;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How other modules ask about current pay without ever reading compensation tables. Current means
 * the salary period that covers today.
 */
public interface CurrentSalaryQuery {

    /** Current salary per employee; people without one are absent from the result. */
    Map<UUID, Money> currentSalaries(Collection<UUID> employeeIds);

    /**
     * The given employees ordered by current salary in USD, highest first when {@code descending}.
     * The caller supplies the rate snapshot, so a request converts everything with the same rates it
     * shows. People without a current salary come last, and equal salaries keep the order given.
     */
    List<UUID> rankByCurrentUsd(Collection<UUID> employeeIds, boolean descending, ExchangeRates rates);
}
