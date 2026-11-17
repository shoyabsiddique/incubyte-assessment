package com.acme.salary.shared.web;

import com.acme.salary.shared.domain.Money;
import com.acme.salary.shared.domain.Rounding;

/** Money as the API shows it: a decimal string rounded for display, with its currency code. */
public record MoneyResponse(String amount, String currency) {

    public static MoneyResponse from(Money money) {
        if (money == null) {
            return null;
        }
        return new MoneyResponse(Rounding.display(money.amount()).toPlainString(), money.currency().getCurrencyCode());
    }
}
