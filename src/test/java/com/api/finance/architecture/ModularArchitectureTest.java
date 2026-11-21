package com.api.finance.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModularArchitectureTest {

    private static JavaClasses applicationClasses;

    @BeforeAll
    static void importApplicationClasses() {
        applicationClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.api.finance");
    }

    @Test
    void controllersMustNotAccessRepositoriesDirectly() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..controller..", "..presentation..")
                .should().dependOnClassesThat()
                .resideInAPackage("..repository..")
                .because("controllers must delegate use cases to application services");

        rule.check(applicationClasses);
    }

    @Test
    void domainPackagesMustNotDependOnFrameworkOrInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "..repository..",
                        "..controller..",
                        "..infrastructure.."
                )
                .because("domain rules must remain independent from frameworks and adapters");

        rule.check(applicationClasses);
    }

    @Test
    void modulesMustNotDependOnOtherModulesPresentationLayer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.api.finance..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.api.finance..controller..", "com.api.finance..presentation..")
                .because("module communication must use application contracts, not another module's HTTP API");

        rule.check(applicationClasses);
    }
    @Test
    void bffAndNotificationMustUseUserApplicationPortInsteadOfUserRepository() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..bff..", "..notification..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.api.finance.user.repository..")
                .because("cross-module user access must go through the public UserDirectoryPort");

        rule.check(applicationClasses);
    }

}
