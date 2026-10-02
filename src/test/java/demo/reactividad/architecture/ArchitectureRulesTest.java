package demo.reactividad.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

class ArchitectureRulesTest {

    private static final JavaClasses ALL_CLASSES = new ClassFileImporter().importPackages("demo.reactividad");

    @Test
    void ordersDomainNeverDependsOnMenuDomainOrApplication() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("demo.reactividad.orders.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "demo.reactividad.domain..",
                        "demo.reactividad.application..");

        rule.check(ALL_CLASSES);
    }

    @Test
    void ordersApplicationNeverDependsOnMenuDomainOrApplication() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("demo.reactividad.orders.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "demo.reactividad.domain..",
                        "demo.reactividad.application..");

        rule.check(ALL_CLASSES);
    }

    @Test
    void ordersNeverDependsOnMenuRepositoryPortDirectly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("demo.reactividad.orders..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("demo.reactividad.application.port.out.MenuRepositoryPort");

        rule.check(ALL_CLASSES);
    }
}
