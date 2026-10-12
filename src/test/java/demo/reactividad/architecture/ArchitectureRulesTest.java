package demo.reactividad.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

class ArchitectureRulesTest {

    private static final JavaClasses ALL_CLASSES = new ClassFileImporter().importPackages("demo.reactividad");

    // Las reglas de abajo verifican mecánicamente la arquitectura hexagonal en sí (la
    // regla de dependencia: domain no sabe nada de las capas de afuera, application solo
    // conoce domain y sus propios puertos) -- hasta ahora esto solo estaba escrito en
    // CLAUDE.md, Sección 1, nunca verificado por un test real.
    @Test
    void domainNeverDependsOnApplicationOrInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("demo.reactividad.domain..", "demo.reactividad.orders.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "demo.reactividad.application..",
                        "demo.reactividad.orders.application..",
                        "demo.reactividad.infrastructure..",
                        "demo.reactividad.orders.infrastructure..");

        rule.check(ALL_CLASSES);
    }

    @Test
    void applicationNeverDependsOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("demo.reactividad.application..", "demo.reactividad.orders.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "demo.reactividad.infrastructure..",
                        "demo.reactividad.orders.infrastructure..");

        rule.check(ALL_CLASSES);
    }

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
