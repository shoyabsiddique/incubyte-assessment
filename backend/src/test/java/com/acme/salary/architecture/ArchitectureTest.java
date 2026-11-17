package com.acme.salary.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Enforces the module structure and dependency rules on the real application code. */
class ArchitectureTest {

    private static final String BASE = "com.acme.salary";
    private static JavaClasses classes;

    @BeforeAll
    static void importProductionClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE);
    }

    @Test
    void theApplicationClassesWereActuallyImported() {
        // Guards against a wrong BASE making every rule vacuously green.
        assertThat(classes.contain(com.acme.salary.SalaryApplication.class)).isTrue();
    }

    @Test
    void everyTopLevelPackageIsAKnownModuleOrTheSharedKernel() {
        assertThat(ArchitectureRules.unknownTopLevelPackages(BASE, classes)).isEmpty();
    }

    @Test
    void layersInsideAModulePointInwardsOnly() {
        ArchitectureRules.applicationDoesNotDependOnWeb(BASE).check(classes);
        ArchitectureRules.persistenceDoesNotDependOnApplicationOrWeb(BASE).check(classes);
        ArchitectureRules.apiDependsOnNoInternalLayer(BASE).check(classes);
    }

    @Test
    void everyModuleHasEveryLayerPackage() {
        assertThat(ArchitectureRules.missingPackages(BASE, classes)).isEmpty();
    }

    @Test
    void servicesThatWriteThroughARepositoryAuditTheirWrites() {
        ArchitectureRules.writingServicesDependOnAuditTrail(BASE).check(classes);
    }

    @Test
    void domainCodeIsFrameworkFree() {
        ArchitectureRules.domainIsFrameworkFree(BASE).check(classes);
    }

    @Test
    void domainDependsOnNoOuterLayer() {
        ArchitectureRules.domainDependsOnNoOuterLayer(BASE).check(classes);
    }

    @Test
    void webDoesNotTouchPersistence() {
        ArchitectureRules.webDoesNotTouchPersistence(BASE).check(classes);
    }

    @Test
    void modulesOnlyUseOtherModulesThroughTheirApiAsTheGraphAllows() {
        List<ArchRule> rules = ArchitectureRules.moduleDependenciesFollowTheGraph(BASE);
        rules.forEach(rule -> rule.check(classes));
    }

    @Test
    void seedUsesOnlySharedAndModuleApisAndNoModuleDependsOnIt() {
        ArchitectureRules.seedUsesOnlySharedAndModuleApis(BASE).check(classes);
        ArchitectureRules.noModuleDependsOnSeed(BASE).check(classes);
    }

    @Test
    void sharedKernelDependsOnNoModule() {
        ArchitectureRules.sharedDependsOnNoModule(BASE).check(classes);
    }

    @Test
    void modulesAreFreeOfCycles() {
        ArchitectureRules.modulesAreFreeOfCycles(BASE).check(classes);
    }
}
