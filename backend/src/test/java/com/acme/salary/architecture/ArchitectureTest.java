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
    void everyModuleHasEveryLayerPackage() {
        assertThat(ArchitectureRules.missingPackages(BASE, classes)).isEmpty();
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
    void sharedKernelDependsOnNoModule() {
        ArchitectureRules.sharedDependsOnNoModule(BASE).check(classes);
    }

    @Test
    void modulesAreFreeOfCycles() {
        ArchitectureRules.modulesAreFreeOfCycles(BASE).check(classes);
    }
}
