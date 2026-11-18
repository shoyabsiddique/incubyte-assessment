package com.acme.salary.analytics.domain;

/** The statistics of one group in USD and, when asked for, in the group's own currency (else null). */
public record GroupStats(String key, Stats usd, Stats local) {}
