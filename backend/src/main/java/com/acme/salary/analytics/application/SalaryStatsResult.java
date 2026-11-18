package com.acme.salary.analytics.application;

import com.acme.salary.analytics.domain.GroupBy;
import com.acme.salary.analytics.domain.GroupStats;
import java.util.List;

public record SalaryStatsResult(GroupBy groupBy, boolean withLocal, List<GroupStats> groups) {}
