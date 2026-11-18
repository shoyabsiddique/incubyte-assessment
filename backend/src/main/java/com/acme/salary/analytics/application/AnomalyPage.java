package com.acme.salary.analytics.application;

import com.acme.salary.analytics.domain.Anomaly;
import java.util.List;

public record AnomalyPage(List<Anomaly> items, int page, int size, long totalElements, int totalPages) {}
