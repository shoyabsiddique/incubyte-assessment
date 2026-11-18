package com.acme.salary.simulator.domain;

import com.acme.salary.analytics.api.UsdPay;
import com.acme.salary.shared.domain.Country;

/** Who a simulated increment applies to: everyone, one country or one department. */
public record SimulationScope(Type type, String value) {

    public enum Type {
        ORGANIZATION,
        COUNTRY,
        DEPARTMENT
    }

    public static SimulationScope organization() {
        return new SimulationScope(Type.ORGANIZATION, null);
    }

    public static SimulationScope country(Country country) {
        return new SimulationScope(Type.COUNTRY, country.name());
    }

    public static SimulationScope department(String department) {
        return new SimulationScope(Type.DEPARTMENT, department);
    }

    boolean includes(UsdPay pay) {
        return switch (type) {
            case ORGANIZATION -> true;
            case COUNTRY -> pay.country().name().equals(value);
            case DEPARTMENT -> pay.department().equals(value);
        };
    }
}
