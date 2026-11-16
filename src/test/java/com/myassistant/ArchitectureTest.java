package com.myassistant;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

// The conventions of AGENTS.md that can be read from the imports, written as tests: the build fails when one breaks.
// allowEmptyShould: the feature packages arrive in Phases 3-5; until then no class matches, and that is not a failure.
@AnalyzeClasses(packages = "com.myassistant", importOptions = DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule templatesDoNotKnowConfigurations = noClasses()
            .that().resideInAPackage("..template..")
            .should().dependOnClassesThat().resideInAPackage("..configuration..")
            .because("configuration may ask the template feature, never the other way round")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .because("a controller talks to its service; the service owns the data")
            .allowEmptyShould(true);
}
