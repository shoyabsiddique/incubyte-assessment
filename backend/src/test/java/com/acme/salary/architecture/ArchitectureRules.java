package com.acme.salary.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The architecture rules from the spine (AD-1, AD-2), parameterised by base package so the same
 * rules can be proven against both the real code and small good/bad fixtures.
 */
final class ArchitectureRules {

    static final List<String> MODULES =
            List.of("employees", "compensation", "analytics", "simulator", "auth", "audit");
    static final List<String> LAYERS = List.of("api", "web", "application", "domain", "persistence");
    static final String SHARED = "shared";

    /** Which modules a module may use (through their api package only). Shared is open to all. */
    static final Map<String, Set<String>> ALLOWED_DEPENDENCIES = Map.of(
            "employees", Set.of("compensation", "audit"),
            "compensation", Set.of("audit"),
            "analytics", Set.of(),
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
                        "jakarta.persistence..",
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
                            .dependOnClassesThat()
                            .resideInAnyPackage(internalPackages(base, other))
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

    private static String[] internalPackages(String base, String module) {
        return new String[] {
            base + "." + module + ".web..",
            base + "." + module + ".application..",
            base + "." + module + ".domain..",
            base + "." + module + ".persistence.."
        };
    }
}
