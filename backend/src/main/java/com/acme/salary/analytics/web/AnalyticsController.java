package com.acme.salary.analytics.web;

import com.acme.salary.analytics.application.AnalyticsService;
import com.acme.salary.analytics.application.AnomalyPage;
import com.acme.salary.analytics.domain.Anomaly;
import com.acme.salary.analytics.application.DepartmentExtremes;
import com.acme.salary.analytics.application.SalaryStatsResult;
import com.acme.salary.analytics.domain.ActiveSalary;
import com.acme.salary.analytics.domain.GroupStats;
import com.acme.salary.analytics.domain.RankedSalary;
import com.acme.salary.analytics.domain.Stats;
import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.web.MoneyResponse;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
class AnalyticsController {

    private final AnalyticsService analytics;

    AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/salary-stats")
    SalaryStatsResponse salaryStats(
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String jobTitle) {
        SalaryStatsResult result = analytics.salaryStats(groupBy, country, department, jobTitle);
        return new SalaryStatsResponse(
                result.groupBy().name().equals("JOB_TITLE") ? "jobTitle" : result.groupBy().name().toLowerCase(),
                result.groups().stream().map(AnalyticsController::group).toList());
    }

    @GetMapping("/pay-extremes")
    PayExtremesResponse payExtremes(@RequestParam(required = false) String department, @RequestParam(required = false) String n) {
        List<DepartmentExtremes> result = analytics.payExtremes(department, n);
        return new PayExtremesResponse(result.stream()
                .map(d -> new DepartmentResponse(
                        d.department(),
                        d.extremes().highest().stream().map(AnalyticsController::person).toList(),
                        d.extremes().lowest().stream().map(AnalyticsController::person).toList()))
                .toList());
    }

    @GetMapping("/anomalies")
    AnomalyPageResponse anomalies(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort) {
        AnomalyPage result = analytics.anomalies(country, department, direction, page, size, sort);
        return new AnomalyPageResponse(
                "Potential anomalies for HR review",
                result.items().stream().map(AnalyticsController::anomaly).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    private static AnomalyResponse anomaly(Anomaly a) {
        ActiveSalary p = a.person();
        return new AnomalyResponse(
                p.id(),
                p.employeeId(),
                p.name(),
                p.country().name(),
                p.country().displayName(),
                p.department(),
                p.jobTitle(),
                MoneyResponse.from(p.salary()),
                MoneyResponse.from(a.usd()),
                MoneyResponse.from(a.median()),
                a.groupSize(),
                a.deviationPercent().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                a.direction().name());
    }

    private static GroupResponse group(GroupStats group) {
        return new GroupResponse(group.key(), label(group.key()), stats(group.usd()), group.local() == null ? null : stats(group.local()));
    }

    /** Country keys read as names ("United Kingdom"); department and job-title keys are already text. */
    private static String label(String key) {
        try {
            return Country.valueOf(key).displayName();
        } catch (IllegalArgumentException notACountry) {
            return key;
        }
    }

    private static StatsResponse stats(Stats s) {
        return new StatsResponse(
                s.count(),
                MoneyResponse.from(s.total()),
                MoneyResponse.from(s.average()),
                MoneyResponse.from(s.median()),
                MoneyResponse.from(s.minimum()),
                MoneyResponse.from(s.maximum()));
    }

    private static PersonResponse person(RankedSalary ranked) {
        ActiveSalary p = ranked.person();
        return new PersonResponse(
                p.id(),
                p.employeeId(),
                p.name(),
                p.jobTitle(),
                p.country().name(),
                p.country().displayName(),
                MoneyResponse.from(p.salary()),
                MoneyResponse.from(ranked.usd()));
    }

    record SalaryStatsResponse(String groupBy, List<GroupResponse> groups) {}

    record GroupResponse(String key, String label, StatsResponse usd, StatsResponse local) {}

    record StatsResponse(
            long count,
            MoneyResponse total,
            MoneyResponse average,
            MoneyResponse median,
            MoneyResponse minimum,
            MoneyResponse maximum) {}

    record PayExtremesResponse(List<DepartmentResponse> departments) {}

    record DepartmentResponse(String department, List<PersonResponse> highest, List<PersonResponse> lowest) {}

    record PersonResponse(
            String id,
            String employeeId,
            String name,
            String jobTitle,
            String country,
            String countryName,
            MoneyResponse salary,
            MoneyResponse salaryUsd) {}

    record AnomalyPageResponse(
            String label, List<AnomalyResponse> items, int page, int size, long totalElements, int totalPages) {}

    record AnomalyResponse(
            String id,
            String employeeId,
            String name,
            String country,
            String countryName,
            String department,
            String jobTitle,
            MoneyResponse salary,
            MoneyResponse salaryUsd,
            MoneyResponse peerMedianUsd,
            int peerGroupSize,
            String deviationPercent,
            String direction) {}
}
