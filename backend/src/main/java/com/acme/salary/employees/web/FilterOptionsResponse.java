package com.acme.salary.employees.web;

import com.acme.salary.employees.application.FilterOptions;
import java.util.List;

record FilterOptionsResponse(
        List<Option> countries,
        List<String> departments,
        List<String> jobTitles,
        List<String> employmentTypes,
        List<String> statuses) {

    record Option(String code, String name) {}

    static FilterOptionsResponse from(FilterOptions options) {
        return new FilterOptionsResponse(
                options.countries().stream().map(c -> new Option(c.name(), c.displayName())).toList(),
                options.departments(),
                options.jobTitles(),
                options.employmentTypes().stream().map(Enum::name).toList(),
                options.statuses().stream().map(Enum::name).toList());
    }
}
