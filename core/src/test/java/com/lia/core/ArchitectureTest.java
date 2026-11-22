package com.lia.core;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.Dependency.Predicates.dependencyTarget;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.conditions.ArchConditions.onlyHaveDependenciesWhere;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.SliceRule;

class ArchitectureTest {

    private static final String CORE = "com.lia.core..";
    private static final String DOMAIN = "com.lia.core.domain..";
    private static final String LAWDATA = "com.lia.core.lawdata..";
    private static final String PIPELINE = "com.lia.core.pipeline..";
    private static final String INGEST = "com.lia.core.ingest..";
    private static final String FEATURE = "com.lia.core.feature..";
    private static final String OBSERVABILITY = "com.lia.core.observability..";

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.lia.core");

    private static final List<KnownException> KNOWN_EXCEPTIONS = List.of(
            new KnownException("com.lia.core.pipeline.analyze.AnalysisEngine", "com.lia.core.eval.FaithfulnessGate"),
            new KnownException("com.lia.core.pipeline.connector.LawConnector", "com.lia.core.config.LiaSourceProperties"),
            new KnownException("com.lia.core.observability.llm.LlmPurpose", "com.lia.core.pipeline.plan.QueryType"),
            new KnownException("com.lia.core.observability.SampleIngestRunner", "com.lia.core.pipeline.connector.RawLaw"),
            new KnownException("com.lia.core.observability.SampleIngestRunner", "com.lia.core.pipeline.diff.DiffBuilder"),
            new KnownException("com.lia.core.observability.SampleIngestRunner", "com.lia.core.pipeline.normalize.Normalizer"));

    private static final Map<String, Set<String>> ALLOWED_FEATURE_DEPENDENCIES = Map.of(
            "history", Set.of("analysis"),
            "analysis", Set.of("law", "profile"),
            "profile", Set.of("account"));

    @Test
    @DisplayName("A1 — domain 은 core 의 다른 패키지를 쓰지 않는다")
    void domainDependsOnNothingInCore() {
        noClasses().that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat(resideInAPackage(CORE).and(resideOutsideOfPackage(DOMAIN)))
                .because("도메인은 가장 천천히 변한다 — 규약 1")
                .check(MAIN);
    }

    @Test
    @DisplayName("A2 — lawdata 는 domain 만 쓴다")
    void lawdataDependsOnlyOnDomain() {
        classes().that().resideInAPackage(LAWDATA)
                .should().onlyDependOnClassesThat(resideOutsideOfPackage(CORE).or(resideInAnyPackage(LAWDATA, DOMAIN)))
                .because("파이프라인·배치·기능이 함께 쓰는 법령 데이터 — 규약 1")
                .check(MAIN);
    }

    @Test
    @DisplayName("A3 — pipeline 은 ingest·auth·config·eval·feature·web 을 쓰지 않는다")
    void pipelineDoesNotKnowItsCallers() {
        DescribedPredicate<Dependency> callerSide = dependencyTarget(resideInAnyPackage(
                INGEST, "com.lia.core.auth..", "com.lia.core.config..", "com.lia.core.eval..", FEATURE, "com.lia.core.web.."));
        classes().that().resideInAPackage(PIPELINE)
                .should(onlyHaveDependenciesWhere(not(callerSide).or(knownException())))
                .because("분석 엔진은 웹 요청·배치·평가 어느 쪽이 부르는지 모른다 — 규약 1")
                .check(MAIN);
    }

    @Test
    @DisplayName("A4 — ingest 는 domain·lawdata·pipeline·observability 만 쓴다")
    void ingestDependsOnlyOnInnerLayers() {
        classes().that().resideInAPackage(INGEST)
                .should().onlyDependOnClassesThat(resideOutsideOfPackage(CORE)
                        .or(resideInAnyPackage(INGEST, DOMAIN, LAWDATA, PIPELINE, OBSERVABILITY)))
                .because("배치는 기능을 모른다 — 기능이 배치 포트를 구현한다(DueNotifications) — 규약 1·2")
                .check(MAIN);
    }

    @Test
    @DisplayName("A5 — feature 를 쓰는 곳은 다른 기능·web·config 뿐이다")
    void onlyOuterLayersDependOnFeatures() {
        noClasses().that().resideOutsideOfPackages(FEATURE, "com.lia.core.web..", "com.lia.core.config..")
                .should().dependOnClassesThat().resideInAPackage(FEATURE)
                .because("안쪽 계층과 auth 는 사용자 기능을 모른다 — 규약 1")
                .check(MAIN);
    }

    @Test
    @DisplayName("A6 — 기능 사이는 허용된 방향만")
    void featuresDependOnEachOtherOnlyInAllowedDirections() {
        classes().that().resideInAPackage(FEATURE)
                .should(onlyHaveDependenciesWhere(allowedBetweenFeatures()))
                .because("기능 하나를 고치면 그 폴더에서 끝나야 한다 — 규약 2")
                .check(MAIN);
    }

    @Test
    @DisplayName("A7 — feature.analysis 안은 web → 유스케이스 → usage·event 방향만")
    void analysisFeatureLayersPointInward() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("web").definedBy("com.lia.core.feature.analysis.web..")
                .layer("usecase").definedBy("com.lia.core.feature.analysis")
                .layer("support").definedBy("com.lia.core.feature.analysis.usage..", "com.lia.core.feature.analysis.event..")
                .whereLayer("web").mayNotBeAccessedByAnyLayer()
                .whereLayer("usecase").mayOnlyBeAccessedByLayers("web")
                .whereLayer("support").mayOnlyBeAccessedByLayers("usecase", "web")
                .because("기능 폴더 안 하위 패키지끼리도 한 방향 — 규약 3")
                .check(MAIN);
    }

    @Test
    @DisplayName("A8 — 최상위 패키지 사이에 순환이 없다")
    void noCyclesBetweenTopLevelPackages() {
        ignoringKnownExceptions(slices().matching("com.lia.core.(*)..").should().beFreeOfCycles())
                .because("순환이 생기면 어느 쪽도 따로 바꿀 수 없다 — 규약 1")
                .check(MAIN);
    }

    @Test
    @DisplayName("A8 — 하위 패키지까지 순환이 없다")
    void noCyclesBetweenAnyPackages() {
        ignoringKnownExceptions(slices().matching("com.lia.core.(**)").should().beFreeOfCycles())
                .because("단계·기능 하위 패키지끼리도 한 방향 — 규약 1·3")
                .check(MAIN);
    }

    @Test
    @DisplayName("B1 — 유스케이스에는 Spring 스테레오타입을 붙이지 않는다")
    void useCasesAreWiredByConfig() {
        classes().that().haveSimpleNameEndingWith("UseCase")
                .should().notBeMetaAnnotatedWith(Component.class)
                .because("유스케이스는 프레임워크 비의존 — config 의 @Bean 으로 배선한다(AnalyzeUseCase.md)")
                .check(MAIN);
    }

    @Test
    @DisplayName("B2 — domain 은 Spring 을 쓰지 않는다")
    void domainIsFrameworkFree() {
        noClasses().that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .because("도메인은 프레임워크도 모른다 — 규약 1")
                .check(MAIN);
    }

    @Test
    @DisplayName("B3 — 컨트롤러는 feature 와 auth 에만 있다")
    void controllersLiveInFeatures() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAnyPackage(FEATURE, "com.lia.core.auth..")
                .because("컨트롤러는 자기 기능 폴더에 — web 은 ApiExceptionHandler 만(D90)")
                .check(MAIN);
    }

    @Test
    @DisplayName("B4 — Raw* 는 커넥터에만 있다")
    void rawTypesStayInConnector() {
        classes().that().haveSimpleNameStartingWith("Raw")
                .should().resideInAPackage("com.lia.core.pipeline.connector")
                .because("Raw* 는 출처 원형을 담은 커넥터 지역 DTO — 규약 4")
                .check(MAIN);
    }

    private static DescribedPredicate<Dependency> knownException() {
        return DescribedPredicate.describe("남은 예외(KNOWN_EXCEPTIONS)",
                dependency -> KNOWN_EXCEPTIONS.stream().anyMatch(known -> known.matches(dependency)));
    }

    private static SliceRule ignoringKnownExceptions(SliceRule rule) {
        SliceRule result = rule;
        for (KnownException known : KNOWN_EXCEPTIONS) {
            result = result.ignoreDependency(classNamed(known.origin()), classNamed(known.target()));
        }
        return result;
    }

    private static DescribedPredicate<Dependency> allowedBetweenFeatures() {
        return DescribedPredicate.describe("기능 간 허용 방향 " + ALLOWED_FEATURE_DEPENDENCIES, dependency -> {
            Optional<String> from = featureOf(dependency.getOriginClass());
            Optional<String> to = featureOf(dependency.getTargetClass());
            if (from.isEmpty() || to.isEmpty() || from.equals(to)) return true;
            return ALLOWED_FEATURE_DEPENDENCIES.getOrDefault(from.get(), Set.of()).contains(to.get());
        });
    }

    private static Optional<String> featureOf(JavaClass javaClass) {
        String prefix = "com.lia.core.feature.";
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(prefix)) return Optional.empty();
        String rest = packageName.substring(prefix.length());
        int dot = rest.indexOf('.');
        return Optional.of(dot < 0 ? rest : rest.substring(0, dot));
    }

    private static DescribedPredicate<JavaClass> classNamed(String name) {
        return DescribedPredicate.describe(name, javaClass -> belongsTo(javaClass, name));
    }

    private static boolean belongsTo(JavaClass javaClass, String name) {
        String actual = javaClass.getName();
        return actual.equals(name) || actual.startsWith(name + "$");
    }

    private record KnownException(String origin, String target) {
        boolean matches(Dependency dependency) {
            return belongsTo(dependency.getOriginClass(), origin) && belongsTo(dependency.getTargetClass(), target);
        }
    }
}
