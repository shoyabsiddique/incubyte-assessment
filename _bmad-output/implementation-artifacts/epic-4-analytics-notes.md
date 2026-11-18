# Epic 4 design notes: analytics

- Rows come from the read-only view `v_active_compensation` (V8): every salary record of every ACTIVE employee. The query picks the record in force on the date of the injected `Clock`; nothing calls `CURDATE()`.
- `analytics` may use the `compensation` api (exchange rate snapshot). The allowed-dependency graph in the architecture rules was widened from `analytics -> {}` to `analytics -> {compensation}` for this.
- Statistics (count, total, average, median, minimum, maximum) are computed in the framework-free `SalaryStatistics` from the rows themselves, in USD at one rate snapshot; the median of an even-sized group is the exact mean of the two middle values. Local-currency figures are added only when a country filter is set.
- Pay extremes: top and bottom `n` (default 5, maximum 25) by USD, ties by Employee ID; the lowest list is drawn from people not already in the highest list, so no one appears twice.
- Highest and lowest lists for a department smaller than `n` show everyone in the highest list and leave the lowest list empty.

## Story 4.4: performance record (10,000 seeded employees, Testcontainers MariaDB 11.4, warm, best of five)

| Endpoint | Time |
| --- | --- |
| `GET /analytics/salary-stats?groupBy=jobTitle` | about 150 ms |
| `GET /analytics/pay-extremes` (all departments) | about 120 ms |

Both are under the 500 ms limit, so no cache was added and Story 4.4 is closed by this record. `AnalyticsIT.recordsHowLongTheAnalyticsEndpointsTakeOnTenThousandEmployees` fails if either exceeds 500 ms, so a regression will show; if it does, add the invalidated in-memory projection cache described in the story.
