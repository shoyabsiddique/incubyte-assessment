# Review: version and technology currency of ARCHITECTURE-SPINE.md

Review date: 2026-11-17. Method: WebSearch plus WebFetch of primary pages where reachable (angular.dev, nodejs.org, Spring Boot wiki, Microsoft Learn). Where only secondary blogs were found, that is marked UNCONFIRMED. The spine was not edited.

## Verdict

PASS WITH FIXES. The architecture holds up: every named technology still exists and fits. The spine is honest that versions were not pinned (it uses "current stable" and two ASSUMPTION tags). But the Stack table has no verified version numbers, and several details are stale or missing. Fixing them takes a few edits and no redesign.

## Findings (priority order)

### 1. Angular 21 vs 22 is resolved: Angular 22 is current (CONFIRMED, angular.dev/reference/versions)
- Angular 22.x, 21.x and 20.x are listed as actively supported.
- Angular 22 requires Node `^22.22.3 || ^24.15.0 || ^26.0.0`, TypeScript `>=6.0 <6.1` and RxJS `^6.5.3 || ^7.4.0`.
- Angular 21 requires Node `^20.19 || ^22.12 || ^24`, TypeScript 5.9.x.
- Action: remove the open ASSUMPTION and pin Angular 22. Pin TypeScript 6.0, which is a new major that trips up some tooling and should be named. The "Node: current LTS meeting the Angular floor" line should say Node 24 or 26 LTS, or Node 22.22.3+.
- nodejs.org lists v26 (26.13.1), v24 (24.22.3) and v22 (22.23.5) as LTS lines, and v20 and older as EOL. I could not confirm which line is "Active" and which is "Maintenance". Pick Node 24 for CI and the Azure build, because it satisfies both Angular 21 and 22.
- Playwright is not pinned either; see finding 5.

### 2. Spring Boot 4.1.x is real, but the spine's compatibility notes are stale or incomplete (PARTLY CONFIRMED)
- Boot 4.1.0 went GA on 2026-06-10 and 4.1.1 shipped 2026-08-20 (spring.io blog; the release notes page is on the wiki). Pin 4.1.1 or the latest 4.1.x patch, not "4.1.x".
- Boot 4.2 is reported as expected around November 2026, which is this month. Say whether to stay on 4.1.x until a 4.2.x patch exists. Do not take 4.2.0.
- The Java baseline stays 17 (secondary sources only; the release notes I read did not state it), so the `[ASSUMPTION: Boot 4 supports 17+]` is probably correct. Java 21 is fine. Close the assumption by citing the Boot system requirements page. I did not verify Azure App Service Java 21 support in this review.
- Missing from the spine, and a real build trap: in Boot 4 modularization, Flyway is no longer auto-configured just because the jar is on the classpath. You need `spring-boot-starter-flyway` (and the `-test` starter for test slices). The spine says only "Flyway ... managed by the Spring Boot BOM". Add the starter requirement to AD-11 or the Stack table. Also add the PostgreSQL JDBC driver and `flyway-database-postgresql`; Flyway 10+ split database support into separate modules. This second point is from training knowledge and is UNCONFIRMED here.
- Boot 4.1 manages Flyway 12.4.0 (from the 4.1 release notes). The spine's "managed by the BOM" is fine, but record the version.
- Boot 4 also changed other things that touch the spine: Jackson 3 is the default (the spine's "imports nothing from Jackson" rule and the `jsonb` audit mapping must be checked against Jackson 3 package names `tools.jackson.*`), and Spring Security 7.x applies (the 4.1 release is reported to ship Spring Security 7.1). These come from training knowledge plus one secondary article. They are UNCONFIRMED against the official migration guide, but should be checked at scaffold.

### 3. Testing stack: "JUnit 5" is probably stale; Testcontainers 2.x changes names (PARTLY CONFIRMED)
- Boot 4.0 moved to JUnit Jupiter 6, to my knowledge (UNCONFIRMED here). The Stack row "JUnit 5" should read "JUnit Jupiter (version from the BOM)". Check `spring-boot-starter-test` at scaffold.
- Testcontainers 2.0 renamed modules with a `testcontainers-` prefix (for example `testcontainers-postgresql`) and Boot 4 expects the 2.x line. Confirmed by secondary sources only (rieckpil.de, dev.to, a migration skill file). Boot manages the version, so the spine's "managed by the BOM" is fine, but any hand-written dependency or import copied from older tutorials will break. A one-line warning in AD-12 would help the implementer.
- Prefer `@ServiceConnection` with `spring-boot-testcontainers`; docs exist for 4.0, 4.0.4 and 4.2 (docs.spring.io appendix pages found).
- ArchUnit: the official site lists 1.4.2 (released 2026-04-18) as the latest. One blog claims 1.5.1 (September 2026); I could not confirm it, so treat it as UNCONFIRMED. ArchUnit is not managed by the Boot BOM. The spine says "managed/current", which is wrong for ArchUnit and could lead to an unpinned dependency. Pin the version explicitly (1.4.2, or newer after checking Maven Central). ArchUnit is alive and fits AD-1/AD-2. Also confirm it reads Java 21 class files; recent releases do.

### 4. PostgreSQL: "17 or current stable" is vague; 18 is current and Azure supports it (CONFIRMED)
- PostgreSQL 18 went GA 2025-09-25. Minor releases 18.4, 17.10, 16.14, 15.18 and 14.23 shipped 2026-05-14. One aggregator claims 18.6 on 2026-08-15; UNCONFIRMED.
- Azure Database for PostgreSQL flexible server lists PG 18 as supported, with Standard Support through 2030-11-14 and 18.4 as the current minor on the mirror I read. PG 17 is also supported.
- Action: pin one major (18 recommended) in docker-compose, Testcontainers and the Azure server, so local, test and prod match. The spine's "17 or current stable" invites a mismatch between Testcontainers and Azure, which defeats AD-12's "authoritative DB environment" claim.
- Product naming: Microsoft's docs now say "Azure Database for PostgreSQL" with "flexible server" as the deployment option. "Flexible Server" in the spine is still understood, but use the current wording.

### 5. Playwright: version unpinned and sources conflict (UNCONFIRMED)
- Sources show 1.62.1 (July 2026 snapshot), 1.63.0 (a September 2026 post says it is `latest` on npm) and a 1.64 (October 2026) blog post. I could not run `npm view @playwright/test version`.
- Playwright is not managed by anything. Pin the exact version in `e2e/package.json` and let the lockfile hold it. Playwright's browser builds need a matching Node; Node 24 is safe.

### 6. Azure services still exist and fit (CONFIRMED for SWA and PG; App Service not re-verified)
- Azure Static Web Apps is active; the overview page was updated 2026-01-23 and lists Angular, GitHub integration, free SSL and custom domains. No retirement notice seen.
- Fit issue: SWA's built-in "no CORS needed" reverse proxy only applies when the API is linked as a backend, which needs the Standard plan. The spine instead has the SPA call App Service directly with CORS restricted to the frontend origin (AD-11). That works on the Free plan, but state that choice, because the SWA docs steer you toward the linked-backend model.
- SWA needs a `staticwebapp.config.json` navigation fallback for Angular deep links (training knowledge; UNCONFIRMED).
- Azure App Service for a Spring Boot jar: not re-verified. Confirm Java 21 is offered at scaffold, and that the chosen SKU is not Free/F1 (always-on, JVM memory).
- GitHub Actions: not checked. Pin action major versions in the workflow.

### 7. Other items not verified
- Gradle: "wrapper, current stable" is fine, but Boot 4.1 requires a minimum Gradle version that I could not read (the release notes were truncated). Generating from start.spring.io avoids the issue.
- Angular Material and RxJS versions: Angular 22 allows RxJS 7.4+. Material tracks Angular, which is standard.
- Mockito and AssertJ: BOM-managed; no check needed.
- BCrypt and Spring Security JWT: the spine says "Spring Security, stateless JWT". In Boot 4.1 / Security 7 the resource-server support (`spring-boot-starter-oauth2-resource-server`) is the usual route; the spine does not say which library signs and verifies the token. Not a version issue, but an unstated decision.

## What was verified directly versus not

| Claim | Status |
| --- | --- |
| Spring Boot 4.1.x exists; 4.1.0 on 2026-06-10, 4.1.1 on 2026-08-20 | Confirmed (spring.io, secondary articles) |
| Boot 4.2 arriving in November 2026 | Reported by one search summary; unconfirmed |
| Java 17 baseline, Java 21 fine | Probable; not stated on the pages read |
| Flyway needs `spring-boot-starter-flyway` in Boot 4 | Confirmed by multiple secondary sources |
| Flyway 12.4.0 in Boot 4.1 | Confirmed (Boot 4.1 wiki) |
| Testcontainers 2.x module renames | Secondary sources only |
| JUnit 6 in Boot 4 | Training knowledge only; not checked |
| Angular 22 current; Node and TypeScript floors | Confirmed (angular.dev) |
| Node 22, 24 and 26 are LTS lines | Confirmed (nodejs.org) |
| PostgreSQL 18 GA; Azure supports 18 | Confirmed (Microsoft Learn, postgresql.org) |
| ArchUnit 1.4.2 latest official | Confirmed on archunit.org; 1.5.1 claim unconfirmed |
| Playwright 1.63 or 1.64 | Conflicting; unconfirmed |
| Azure Static Web Apps active | Confirmed (Microsoft Learn) |
| App Service Java 21; Jackson 3; Security 7 | Not verified here |

## Recommended edits to the spine (for the owner to apply)
1. Replace Stack with pinned values: Angular 22.x, TypeScript 6.0, Node 24 LTS, PostgreSQL 18, Spring Boot 4.1.x (latest patch), Java 21, ArchUnit pinned explicitly, Playwright pinned in the lockfile.
2. Remove both `[ASSUMPTION]` tags once the pins are in.
3. Add the Boot 4 starter requirement (`spring-boot-starter-flyway` plus the PostgreSQL Flyway module) and a note on Testcontainers 2.x names to AD-11 and AD-12.
4. Change "JUnit 5" to "JUnit Jupiter (BOM version)" after checking `spring-boot-starter-test`.
5. State the CORS-versus-linked-backend choice for SWA, and name the JWT library.
6. Check the Jackson 3 impact on AD-1 and the JSONB audit columns at scaffold.
