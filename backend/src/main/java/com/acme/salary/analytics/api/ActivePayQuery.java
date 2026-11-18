package com.acme.salary.analytics.api;

import java.util.List;

/** The shared read model other modules build on: every active employee's current pay in USD. */
public interface ActivePayQuery {

    /** One entry per active employee, all converted with the same rates and for the same day. */
    List<UsdPay> activePay();
}
