package com.zekodnix.zaramoney;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Layering rules (ArchUnit).
 *
 * <p>Run as a plain JUnit Jupiter {@code @Test}: with the ArchUnit JUnit 5 engine ({@code @AnalyzeClasses} +
 * {@code @ArchTest} fields) surefire reported "Tests run: 0" and never failed, so the rules were silently unchecked.
 *
 * <p>The one deliberate exception: {@code web.rest.errors} ({@code ApiException} & co.) is the API error vocabulary,
 * thrown from every layer (services and security filters included) and translated to Problem JSON by the web layer.
 * Request bodies that a service needs live in {@code service.dto}, not in {@code web.rest.vm}.
 */
class TechnicalStructureTest {

    // prettier-ignore
    static final ArchRule respectsTechnicalArchitectureLayers = layeredArchitecture()
        .consideringAllDependencies()
        .layer("Config").definedBy("..config..")
        .layer("Web").definedBy("..web..")
        .optionalLayer("Service").definedBy("..service..")
        .layer("Security").definedBy("..security..")
        .optionalLayer("Persistence").definedBy("..repository..")
        .layer("Domain").definedBy("..domain..")

        .whereLayer("Config").mayNotBeAccessedByAnyLayer()
        .whereLayer("Web").mayOnlyBeAccessedByLayers("Config")
        .whereLayer("Service").mayOnlyBeAccessedByLayers("Web", "Config")
        .whereLayer("Security").mayOnlyBeAccessedByLayers("Config", "Service", "Web")
        .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service", "Security", "Web", "Config")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Persistence", "Service", "Security", "Web", "Config")

        .ignoreDependency(belongToAnyOf(ZaramoneybackendApp.class), alwaysTrue())
        .ignoreDependency(alwaysTrue(), belongToAnyOf(
            com.zekodnix.zaramoney.config.Constants.class,
            com.zekodnix.zaramoney.config.ApplicationProperties.class
        ))
        // the API error vocabulary (ApiException) may be thrown from any layer
        .ignoreDependency(alwaysTrue(), resideInAPackage("com.zekodnix.zaramoney.web.rest.errors.."));

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackagesOf(ZaramoneybackendApp.class);
    }

    @Test
    void respectsTechnicalArchitectureLayers() {
        respectsTechnicalArchitectureLayers.check(classes);
    }

    @Test
    void theRulesReallyRun() {
        // guard against a silent "0 tests run" again: the import must see the application's classes
        org.assertj.core.api.Assertions.assertThat(classes.contain(com.zekodnix.zaramoney.service.AuditService.class)).isTrue();
        org.assertj.core.api.Assertions.assertThat(classes.size()).isGreaterThan(100);
    }
}
