package com.claseya;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ADR-027: the module graph must stay acyclic. The specific rule also prevents the two cycles that
 * existed before the ports were introduced: {@code teacher -> availability} and
 * {@code teacher -> verification}.
 */
@AnalyzeClasses(packages = "com.claseya", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule modulesAreFreeOfCycles =
            slices().matching("com.claseya.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule teacherDoesNotDependOnAvailabilityOrVerification =
            noClasses().that().resideInAPackage("..teacher..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..availability..", "..verification..");
}
