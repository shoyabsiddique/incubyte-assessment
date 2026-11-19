package com.acme.salary.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.shared.domain.Country;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PivotCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 1, 1);

    private static DashboardRow row(Country country, String department, String title, String employmentType, String hired, String usd) {
        return new DashboardRow(
                country, department, title, "City", employmentType, LocalDate.parse(hired), new BigDecimal(usd));
    }

    private static final List<DashboardRow> ROWS = List.of(
            row(Country.INDIA, "Eng", "Dev", "FULL_TIME", "2020-01-01", "30000"),
            row(Country.INDIA, "Eng", "Dev", "CONTRACT", "2024-01-01", "50000"),
            row(Country.INDIA, "Sales", "Rep", "FULL_TIME", "2024-06-01", "20000"),
            row(Country.USA, "Eng", "Dev", "FULL_TIME", "2021-01-01", "100000"));

    private static PivotResult pivot(Dimension x, Dimension series, Measure measure, Aggregation aggregation) {
        return PivotCalculator.pivot(ROWS, x, series, measure, aggregation, TODAY);
    }

    private static BigDecimal value(PivotResult result, String seriesKey, String category) {
        return result.series().stream()
                .filter(s -> s.key().equals(seriesKey))
                .flatMap(s -> s.points().stream())
                .filter(p -> p.category().equals(category))
                .findFirst()
                .orElseThrow()
                .value();
    }

    @Test
    void countsPeopleByOneDimension() {
        PivotResult r = pivot(Dimension.COUNTRY, null, Measure.HEADCOUNT, Aggregation.COUNT);

        assertThat(r.categories()).containsExactly("INDIA", "USA");
        assertThat(r.series()).hasSize(1);
        assertThat(value(r, "all", "INDIA")).isEqualByComparingTo("3");
        assertThat(value(r, "all", "USA")).isEqualByComparingTo("1");
    }

    @Test
    void aggregatesSalaryInUsd() {
        assertThat(value(pivot(Dimension.DEPARTMENT, null, Measure.SALARY_USD, Aggregation.SUM), "all", "Eng"))
                .isEqualByComparingTo("180000");
        assertThat(value(pivot(Dimension.DEPARTMENT, null, Measure.SALARY_USD, Aggregation.AVERAGE), "all", "Eng"))
                .isEqualByComparingTo("60000");
        assertThat(value(pivot(Dimension.DEPARTMENT, null, Measure.SALARY_USD, Aggregation.MINIMUM), "all", "Eng"))
                .isEqualByComparingTo("30000");
        assertThat(value(pivot(Dimension.DEPARTMENT, null, Measure.SALARY_USD, Aggregation.MAXIMUM), "all", "Eng"))
                .isEqualByComparingTo("100000");
        assertThat(value(pivot(Dimension.DEPARTMENT, null, Measure.SALARY_USD, Aggregation.MEDIAN), "all", "Eng"))
                .isEqualByComparingTo("50000");
    }

    @Test
    void theMedianOfAnEvenGroupIsTheExactMeanOfTheMiddleTwo() {
        assertThat(value(pivot(Dimension.COUNTRY, null, Measure.SALARY_USD, Aggregation.MEDIAN), "all", "INDIA"))
                .isEqualByComparingTo("30000");
        List<DashboardRow> two = List.of(
                row(Country.UK, "Eng", "Dev", "FULL_TIME", "2020-01-01", "10"),
                row(Country.UK, "Eng", "Dev", "FULL_TIME", "2020-01-01", "21"));
        PivotResult r = PivotCalculator.pivot(two, Dimension.COUNTRY, null, Measure.SALARY_USD, Aggregation.MEDIAN, TODAY);
        assertThat(value(r, "all", "UK")).isEqualByComparingTo("15.5");
    }

    @Test
    void aSecondDimensionSplitsEachCategoryIntoSeries() {
        PivotResult r = pivot(Dimension.COUNTRY, Dimension.EMPLOYMENT_TYPE, Measure.HEADCOUNT, Aggregation.COUNT);

        assertThat(r.series()).extracting(PivotSeries::key).containsExactly("CONTRACT", "FULL_TIME");
        assertThat(value(r, "FULL_TIME", "INDIA")).isEqualByComparingTo("2");
        assertThat(value(r, "CONTRACT", "INDIA")).isEqualByComparingTo("1");
        assertThat(value(r, "CONTRACT", "USA")).isEqualByComparingTo("0");
    }

    @Test
    void tenureIsMeasuredInYearsUpToToday() {
        PivotResult r = pivot(Dimension.COUNTRY, null, Measure.TENURE_YEARS, Aggregation.MAXIMUM);

        assertThat(value(r, "all", "INDIA").doubleValue()).isBetween(5.99, 6.01);
        assertThat(value(r, "all", "USA").doubleValue()).isBetween(4.99, 5.01);
    }

    @Test
    void hireYearIsADimension() {
        PivotResult r = pivot(Dimension.HIRE_YEAR, null, Measure.HEADCOUNT, Aggregation.COUNT);

        assertThat(r.categories()).containsExactly("2020", "2021", "2024");
        assertThat(value(r, "all", "2024")).isEqualByComparingTo("2");
    }

    @Test
    void nothingToShowGivesNoCategories() {
        PivotResult r = PivotCalculator.pivot(List.of(), Dimension.COUNTRY, null, Measure.HEADCOUNT, Aggregation.COUNT, TODAY);

        assertThat(r.categories()).isEmpty();
        assertThat(r.series()).isEmpty();
    }

    @Test
    void headcountMeasureOnlyMakesSenseWithCountAndSalaryWithTheOthers() {
        assertThat(Aggregation.COUNT.appliesTo(Measure.HEADCOUNT)).isTrue();
        assertThat(Aggregation.SUM.appliesTo(Measure.HEADCOUNT)).isFalse();
        assertThat(Aggregation.COUNT.appliesTo(Measure.SALARY_USD)).isTrue();
        assertThat(Aggregation.AVERAGE.appliesTo(Measure.SALARY_USD)).isTrue();
    }

    @Test
    void everyDimensionHasAnApiNameAndALabelForItsKeys() {
        assertThat(Dimension.find("jobTitle")).contains(Dimension.JOB_TITLE);
        assertThat(Dimension.find("hireYear")).contains(Dimension.HIRE_YEAR);
        assertThat(Dimension.find("nope")).isEmpty();
        assertThat(Measure.find("salaryUsd")).contains(Measure.SALARY_USD);
        assertThat(Aggregation.find("median")).contains(Aggregation.MEDIAN);
    }
}
