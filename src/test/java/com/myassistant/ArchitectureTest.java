package com.myassistant;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

// The conventions of AGENTS.md that can be read from the imports, written as tests: the build fails when one breaks.
@AnalyzeClasses(packages = "com.myassistant", importOptions = DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule templatesDoNotKnowConfigurations = noClasses()
            .that().resideInAPackage("..template..")
            .should().dependOnClassesThat().resideInAPackage("..configuration..")
            .because("configuration may ask the template feature, never the other way round");

    // Registration only announces the new user (UserRegistered); the starter kit is built by the configuration feature.
    @ArchTest
    static final ArchRule authDoesNotKnowTemplatesOrConfigurations = noClasses()
            .that().resideInAPackage("..auth..")
            .should().dependOnClassesThat().resideInAnyPackage("..template..", "..configuration..")
            .because("auth stays thin: the starter kit listens to the UserRegistered event");

    @ArchTest
    static final ArchRule onlyTemplatesUseTheTemplateRepository = noClasses()
            .that().resideOutsideOfPackage("..template..")
            .should().dependOnClassesThat().haveSimpleName("TemplateRepository")
            .because("other features read templates through TemplateService, which scopes them by owner");

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .because("a controller talks to its service; the service owns the data");
}
