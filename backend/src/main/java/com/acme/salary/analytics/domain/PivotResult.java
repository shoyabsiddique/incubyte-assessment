package com.acme.salary.analytics.domain;

import java.util.List;

/** Every category on the x axis and, for each series, one point per category (zero where nobody falls in). */
public record PivotResult(List<String> categories, List<PivotSeries> series) {}
