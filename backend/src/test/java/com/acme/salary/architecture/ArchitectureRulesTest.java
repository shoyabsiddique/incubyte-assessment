package com.acme.salary.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Proves the rules themselves work: each one must flag a deliberate violation in the "bad"
 * fixtures and accept the "good" fixtures. Without this, a rule that silently matches nothing
 * would look green forever.
 */
class ArchitectureRulesTest {

    private static final String BAD = "com.acme.archfixture.bad";
    private static final String GOOD = "com.acme.archfixture.good";

    private static JavaClasses bad;
    private static JavaClasses good;

    @BeforeAll
    static void importFixtures() {
        ClassFileImporter importer = new ClassFileImporter();
        bad = importer.importPackages(BAD);
        good = importer.importPackages(GOOD);
    }

    @Test
    void frameworkImportInDomainIsFlagged() {
        assertViolation(ArchitectureRules.domainIsFrameworkFree(BAD), bad);
        assertNoViolation(ArchitectureRules.domainIsFrameworkFree(GOOD), good);
    }

    @Test
    void domainReachingIntoApplicationLayerIsFlagged() {
        assertViolation(ArchitectureRules.domainDependsOnNoOuterLayer(BAD), bad);
        assertNoViolation(ArchitectureRules.domainDependsOnNoOuterLayer(GOOD), good);
    }

    @Test
    void webReachingPersistenceIsFlagged() {
        assertViolation(ArchitectureRules.webDoesNotTouchPersistence(BAD), bad);
        assertNoViolation(ArchitectureRules.webDoesNotTouchPersistence(GOOD), good);
    }

    @Test
    void crossModuleImportsOutsideTheGraphOrApiAreFlagged() {
        List<ArchRule> badRules = ArchitectureRules.moduleDependenciesFollowTheGraph(BAD);
        assertThatThrownBy(() -> badRules.forEach(rule -> rule.check(bad)))
                .isInstanceOf(AssertionError.class);

        List<ArchRule> goodRules = ArchitectureRules.moduleDependenciesFollowTheGraph(GOOD);
        assertThatCode(() -> goodRules.forEach(rule -> rule.check(good))).doesNotThrowAnyException();
    }

    @Test
    void eachSpecificForbiddenCrossModuleImportIsCaught() {
        List<String> failing = ArchitectureRules.moduleDependenciesFollowTheGraph(BAD).stream()
                .filter(rule -> fails(rule, bad))
                .map(ArchRule::getDescription)
                .toList();

        // analytics may not use employees at all
        assertThat(failing).anyMatch(d -> d.contains("bad.analytics..") && d.contains("bad.employees.."));
        // compensation may not use employees (the graph points the other way)
        assertThat(failing).anyMatch(d -> d.contains("bad.compensation..") && d.contains("bad.employees.."));
        // simulator may use analytics, but only its api
        assertThat(failing)
                .anyMatch(d -> d.contains("bad.simulator..") && d.contains("internal classes of module analytics"));
        // employees may use compensation, but not classes in its root package
        assertThat(failing)
                .anyMatch(d -> d.contains("bad.employees..") && d.contains("internal classes of module compensation"));
    }

    @Test
    void layerInversionsInsideAModuleAreFlagged() {
        assertViolation(ArchitectureRules.applicationDoesNotDependOnWeb(BAD), bad);
        assertNoViolation(ArchitectureRules.applicationDoesNotDependOnWeb(GOOD), good);
        assertViolation(ArchitectureRules.persistenceDoesNotDependOnApplicationOrWeb(BAD), bad);
        assertNoViolation(ArchitectureRules.persistenceDoesNotDependOnApplicationOrWeb(GOOD), good);
        assertViolation(ArchitectureRules.apiDependsOnNoInternalLayer(BAD), bad);
        assertNoViolation(ArchitectureRules.apiDependsOnNoInternalLayer(GOOD), good);
    }

    @Test
    void unknownTopLevelPackagesAreReported() {
        assertThat(ArchitectureRules.unknownTopLevelPackages(BAD, bad)).containsExactly("reports");
        assertThat(ArchitectureRules.unknownTopLevelPackages(GOOD, good)).isEmpty();
    }

    @Test
    void sharedKernelDependingOnAModuleIsFlagged() {
        assertViolation(ArchitectureRules.sharedDependsOnNoModule(BAD), bad);
        assertNoViolation(ArchitectureRules.sharedDependsOnNoModule(GOOD), good);
    }

    @Test
    void dependencyCyclesBetweenModulesAreFlagged() {
        assertViolation(ArchitectureRules.modulesAreFreeOfCycles(BAD), bad);
        assertNoViolation(ArchitectureRules.modulesAreFreeOfCycles(GOOD), good);
    }

    @Test
    void missingModulePackagesAreReported() {
        List<String> missing = ArchitectureRules.missingPackages(GOOD, good);
        assertThat(missing).contains(GOOD + ".employees.persistence", GOOD + ".analytics.api");
        assertThat(missing).doesNotContain(GOOD + ".employees.application", GOOD + ".shared");
    }

    private static void assertViolation(ArchRule rule, JavaClasses classes) {
        assertThatThrownBy(() -> rule.check(classes)).isInstanceOf(AssertionError.class);
    }

    private static void assertNoViolation(ArchRule rule, JavaClasses classes) {
        assertThatCode(() -> rule.check(classes)).doesNotThrowAnyException();
    }

    private static boolean fails(ArchRule rule, JavaClasses classes) {
        try {
            rule.check(classes);
            return false;
        } catch (AssertionError e) {
            return true;
        }
    }
}
