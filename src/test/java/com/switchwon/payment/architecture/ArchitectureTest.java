package com.switchwon.payment.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.Repository;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

class ArchitectureTest {
    private static final String PAYMENT_DOMAIN = "..payment.domain..";
    private static final String WALLET_DOMAIN = "..wallet.domain..";

    private static JavaClasses classesUnderTest;

    @BeforeAll
    static void importClasses() {
        classesUnderTest = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.switchwon.payment");
    }

    @Nested
    @DisplayName("도메인 순수성")
    class DomainPurity {

        @Test
        @DisplayName("도메인은 스프링에 의존하지 않는다")
        void noSpringDependency() {
            noClasses().that().resideInAnyPackage(PAYMENT_DOMAIN, WALLET_DOMAIN)
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                    .check(classesUnderTest);
        }

        @Test
        @DisplayName("도메인은 JPA에 의존하지 않는다")
        void noJpaDependency() {
            noClasses().that().resideInAnyPackage(PAYMENT_DOMAIN, WALLET_DOMAIN)
                    .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..")
                    .check(classesUnderTest);
        }

        @Test
        @DisplayName("도메인은 검증 애노테이션에 의존하지 않는다")
        void noValidationDependency() {
            noClasses().that().resideInAnyPackage(PAYMENT_DOMAIN, WALLET_DOMAIN)
                    .should().dependOnClassesThat().resideInAnyPackage("jakarta.validation..")
                    .check(classesUnderTest);
        }

        @Test
        @DisplayName("도메인은 롬복을 쓰지 않는다")
        void noLombokDependency() {
            noClasses().that().resideInAnyPackage(PAYMENT_DOMAIN, WALLET_DOMAIN)
                    .should().dependOnClassesThat().resideInAnyPackage("lombok..")
                    .check(classesUnderTest);
        }

        @Test
        @DisplayName("도메인은 인프라를 알지 못한다")
        void noInfraDependency() {
            noClasses().that().resideInAnyPackage(PAYMENT_DOMAIN, WALLET_DOMAIN)
                    .should().dependOnClassesThat().resideInAnyPackage("..infra..")
                    .check(classesUnderTest);
        }

        @Test
        @DisplayName("도메인은 금액에 실수형을 쓰지 않는다")
        void noFloatingPointInDomain() {
            noClasses().that().resideInAnyPackage(PAYMENT_DOMAIN, WALLET_DOMAIN)
                    .should().dependOnClassesThat().haveFullyQualifiedName("java.lang.Double")
                    .orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.Float")
                    .check(classesUnderTest);
        }
    }

    @Nested
    @DisplayName("계층 경계")
    class LayerBoundary {

        @Test
        @DisplayName("서비스는 리포지토리에 직접 의존하지 않는다")
        void serviceDoesNotTouchRepository() {
            noClasses().that().haveSimpleNameEndingWith("Service")
                    .should().dependOnClassesThat().areAssignableTo(Repository.class)
                    .check(classesUnderTest);
        }

        @Test
        @DisplayName("컨트롤러는 리포지토리에 직접 의존하지 않는다")
        void controllerDoesNotTouchRepository() {
            noClasses().that().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat().areAssignableTo(Repository.class)
                    .check(classesUnderTest);
        }

        @Test
        @DisplayName("컨트롤러는 엔티티를 노출하지 않는다")
        void controllerDoesNotExposeEntity() {
            noClasses().that().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat(
                            resideInAPackage("com.switchwon.payment..")
                                    .and(simpleNameEndingWith("Entity")))
                    .check(classesUnderTest);
        }

        @Test
        @DisplayName("저장소 캡슐은 인프라 패키지에만 존재한다")
        void storeResidesInInfra() {
            classes().that().haveSimpleNameEndingWith("Store")
                    .should().resideInAPackage("..infra..")
                    .check(classesUnderTest);
        }

        @Test
        @DisplayName("엔티티는 인프라 패키지에만 존재한다")
        void entityResidesInInfra() {
            classes().that().haveSimpleNameEndingWith("Entity")
                    .should().resideInAPackage("..infra..")
                    .check(classesUnderTest);
        }

        @Test
        @DisplayName("게이트웨이를 호출하는 오케스트레이터는 저장소를 직접 잡지 않는다")
        void orchestratorDoesNotTouchStore() {
            noClasses().that().haveSimpleName("PaymentService").or().haveSimpleName("ReconcileService")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Store")
                    .check(classesUnderTest);
        }

        @Test
        @DisplayName("게이트웨이를 호출하는 오케스트레이터에는 트랜잭션 경계가 없다")
        void transactionalOnlyInTransactionService() {
            noClasses().that().haveSimpleName("PaymentService").or().haveSimpleName("ReconcileService")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.transaction.annotation.Transactional")
                    .check(classesUnderTest);
        }

        @Test
        @DisplayName("컨트롤러는 스프링 페이지 타입을 응답으로 내보내지 않는다")
        void controllerDoesNotExposeSpringPage() {
            noMethods().that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
                    .and().arePublic()
                    .should().haveRawReturnType(org.springframework.data.domain.Page.class)
                    .check(classesUnderTest);
        }
    }

    @Nested
    @DisplayName("의존성 주입")
    class DependencyInjection {

        @Test
        @DisplayName("필드 주입을 쓰지 않는다")
        void noFieldInjection() {
            fields().should().notBeAnnotatedWith(Autowired.class)
                    .check(classesUnderTest);
        }
    }
}
