package com.acme.salary.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The architecture rules from the spine (AD-1, AD-2), parameterised by base package so the same
 * rules can be proven against both the real code and small good/bad fixtures.
 */
final class ArchitectureRules {

    static final List<String> MODULES =
            List.of("employees", "compensation", "analytics", "simulator", "auth", "audit");
    static final List<String> LAYERS = List.of("api", "web", "application", "domain", "persistence");
    static final String SHARED = "shared";

    /** Bootstrap code (the demo-data seed): outside the modules, allowed to use shared and module apis. */
    static final String SEED = "seed";

    /** Which modules a module may use (through their api package only). Shared is open to all. */
    static final Map<String, Set<String>> ALLOWED_DEPENDENCIES = Map.of(
            "employees", Set.of("compensation", "audit"),
            "compensation", Set.of("audit"),
            "analytics", Set.of("compensation"),
            "simulator", Set.of("analytics"),
            "auth", Set.of(),
            "audit", Set.of());

    private ArchitectureRules() {}

    /** AD-1: domain code imports no framework. */
    static ArchRule domainIsFrameworkFree(String base) {
        return noClasses()
                .that()
                .resideInAPackage(base + "..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "org.hibernate..",
                        "com.fasterxml.jackson..",
                        "tools.jackson..")
                .allowEmptyShould(true);
    }

    /** Layering inside a module: domain stands alone; web never reaches persistence. */
    static ArchRule domainDependsOnNoOuterLayer(String base) {
        return noClasses()
                .that()
                .resideInAPackage(base + "..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(base + "..web..", base + "..application..", base + "..persistence..")
                .allowEmptyShould(true);
    }

    static ArchRule webDoesNotTouchPersistence(String base) {
        return noClasses()
                .that()
                .resideInAPackage(base + "..web..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(base + "..persistence..")
                .allowEmptyShould(true);
    }

    /** AD-2: a module uses another module only if the graph allows it, and then only its api. */
    static List<ArchRule> moduleDependenciesFollowTheGraph(String base) {
        List<ArchRule> rules = new ArrayList<>();
        for (String module : MODULES) {
            for (String other : MODULES) {
                if (other.equals(module)) {
                    continue;
                }
                if (ALLOWED_DEPENDENCIES.get(module).contains(other)) {
                    rules.add(noClasses()
                            .that()
                            .resideInAPackage(base + "." + module + "..")
                            .should()
                            .dependOnClassesThat(internalTo(base, other))
                            .allowEmptyShould(true));
                } else {
                    rules.add(noClasses()
                            .that()
                            .resideInAPackage(base + "." + module + "..")
                            .should()
                            .dependOnClassesThat()
                            .resideInAPackage(base + "." + other + "..")
                            .allowEmptyShould(true));
                }
            }
        }
        return rules;
    }

    /** The shared kernel depends on nothing else in the system. */
    static ArchRule sharedDependsOnNoModule(String base) {
        String[] modulePackages =
                MODULES.stream().map(m -> base + "." + m + "..").toArray(String[]::new);
        return noClasses()
                .that()
                .resideInAPackage(base + "." + SHARED + "..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(modulePackages)
                .allowEmptyShould(true);
    }

    /** Inside a module the layers only point inwards: application never uses web. */
    static ArchRule applicationDoesNotDependOnWeb(String base) {
        return noClasses()
                .that()
                .resideInAPackage(base + "..application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(base + "..web..")
                .allowEmptyShould(true);
    }

    static ArchRule persistenceDoesNotDependOnApplicationOrWeb(String base) {
        return noClasses()
                .that()
                .resideInAPackage(base + "..persistence..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(base + "..application..", base + "..web..")
                .allowEmptyShould(true);
    }

    /** A module's api is its published surface, so it must not expose or pull in internals. */
    static ArchRule apiDependsOnNoInternalLayer(String base) {
        return noClasses()
                .that()
                .resideInAPackage(base + "..api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        base + "..web..", base + "..application..", base + "..persistence..")
                .allowEmptyShould(true);
    }

    /** Top-level packages under the base that are neither a known module nor the shared kernel. */
    static Set<String> unknownTopLevelPackages(String base, JavaClasses classes) {
        Set<String> unknown = new TreeSet<>();
        String prefix = base + ".";
        for (JavaClass c : classes) {
            String pkg = c.getPackageName();
            if (!pkg.startsWith(prefix)) {
                continue;
            }
            String top = pkg.substring(prefix.length()).split("\\.")[0];
            if (!MODULES.contains(top) && !SHARED.equals(top) && !SEED.equals(top)) {
                unknown.add(top);
            }
        }
        return unknown;
    }

    /** The seed may write data, but only through shared types and the published api of each module. */
    static ArchRule seedUsesOnlySharedAndModuleApis(String base) {
        DescribedPredicate<JavaClass> moduleInternals = MODULES.stream()
                .map(module -> internalTo(base, module))
                .reduce((left, right) -> left.or(right))
                .orElseThrow()
                .as("internal classes of any module");
        return noClasses()
                .that()
                .resideInAPackage(base + "." + SEED + "..")
                .should()
                .dependOnClassesThat(moduleInternals)
                .allowEmptyShould(true);
    }

    /** Nothing in the application may rely on the seed. */
    static ArchRule noModuleDependsOnSeed(String base) {
        String[] callers = new String[MODULES.size() + 1];
        for (int i = 0; i < MODULES.size(); i++) {
            callers[i] = base + "." + MODULES.get(i) + "..";
        }
        callers[MODULES.size()] = base + "." + SHARED + "..";
        return noClasses()
                .that()
                .resideInAnyPackage(callers)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(base + "." + SEED + "..")
                .allowEmptyShould(true);
    }

    /**
     * AD-5: an application service that writes through a repository must also use AuditTrail, unless
     * it is marked AuditedByCaller because the calling operation records the single entry.
     */
    static ArchRule writingServicesDependOnAuditTrail(String base) {
        ArchCondition<JavaClass> auditsItsWrites =
                new ArchCondition<>("depend on AuditTrail when writing through a repository") {
                    @Override
                    public void check(JavaClass service, ConditionEvents events) {
                        boolean writes = service.getMethodCallsFromSelf().stream()
                                .anyMatch(call -> call.getTargetOwner()
                                                .isAssignableTo("org.springframework.data.repository.Repository")
                                        && (call.getName().startsWith("save") || call.getName().startsWith("delete")));
                        boolean audits = service.getDirectDependenciesFromSelf().stream()
                                .anyMatch(dependency -> dependency
                                        .getTargetClass()
                                        .getName()
                                        .equals(base + ".audit.api.AuditTrail"));
                        boolean exempt = service.isAnnotatedWith(base + ".audit.api.AuditedByCaller");
                        if (writes && !audits && !exempt) {
                            events.add(SimpleConditionEvent.violated(
                                    service, service.getName() + " writes through a repository without AuditTrail"));
                        }
                    }
                };
        return ArchRuleDefinition.classes()
                .that()
                .resideInAnyPackage(base + ".employees.application..", base + ".compensation.application..")
                .should(auditsItsWrites)
                .allowEmptyShould(true);
    }

    static ArchRule modulesAreFreeOfCycles(String base) {
        return SlicesRuleDefinition.slices()
                .matching(base + ".(*)..")
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true);
    }

    /** Every module has every layer package, and shared exists. Returns the missing ones. */
    static List<String> missingPackages(String base, JavaClasses classes) {
        List<String> missing = new ArrayList<>();
        for (String module : MODULES) {
            for (String layer : LAYERS) {
                String pkg = base + "." + module + "." + layer;
                if (!hasClassIn(classes, pkg)) {
                    missing.add(pkg);
                }
            }
        }
        if (!hasClassIn(classes, base + "." + SHARED)) {
            missing.add(base + "." + SHARED);
        }
        return missing;
    }

    private static boolean hasClassIn(JavaClasses classes, String pkg) {
        for (JavaClass c : classes) {
            if (c.getPackageName().equals(pkg) || c.getPackageName().startsWith(pkg + ".")) {
                return true;
            }
        }
        return false;
    }

    /** Anything of the module that is not in its api package, including its root package. */
    private static DescribedPredicate<JavaClass> internalTo(String base, String module) {
        return JavaClass.Predicates.resideInAPackage(base + "." + module + "..")
                .and(JavaClass.Predicates.resideOutsideOfPackage(base + "." + module + ".api.."))
                .as("internal classes of module " + module);
    }
}
