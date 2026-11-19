package com.acme.salary.analytics.domain;

import java.util.List;

public record PivotSeries(String key, List<PivotPoint> points) {}
