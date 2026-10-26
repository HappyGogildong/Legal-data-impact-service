package com.lia.core.web;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.lia.core.application.account.AccountUseCase;
import com.lia.core.application.profile.ProfileUseCase;
import com.lia.core.auth.OAuth2LoginSuccessHandler;
import com.lia.core.config.SecurityConfig;
import com.lia.core.domain.law.Law;
import com.lia.core.store.DataStatusStore;
import com.lia.core.store.LawCatalogStore;
import com.lia.core.store.LawSource;
import com.lia.core.store.LawViewStore;
import com.lia.core.store.TrendingWindow;
import com.lia.core.testsupport.InMemoryAccountStore;
import com.lia.core.testsupport.InMemoryUserProfileStore;

/**
 * 웹 슬라이스 공용 컨텍스트 — 실제 SecurityConfig + 실제 컨트롤러 + 인메모리 store.
 * HTTP 계약·인증·CSRF·예외 매핑을 한 번에 검사한다(Boot 4 는 @WebMvcTest 가 별도 모듈이라 최소 컨텍스트).
 * store 는 컨텍스트 공유 싱글턴이므로 테스트마다 새 userId 를 쓴다.
 *
 * @TestConfiguration 인 이유: 최상위 @Configuration 이면 @SpringBootTest 전체 컨텍스트의 컴포넌트 스캔에 잡혀
 * 인메모리 store 빈이 실제 @Repository 와 이름이 충돌한다.
 */
@TestConfiguration
@EnableWebMvc
@EnableWebSecurity
@Import({SecurityConfig.class, ApiExceptionHandler.class,
        ProfileController.class, ConsentController.class, AccountController.class,
        DataStatusController.class, LawController.class, LawViewController.class})
class ApiSliceTestConfig {

    static final String POLICY = "v-test";
    static final OffsetDateTime STATUS_UPDATED_AT = OffsetDateTime.parse("2026-10-23T04:12:00+09:00");
    static final LocalDate LAW_EFFECTIVE = LocalDate.of(2026, 11, 1);
    static final LocalDate LAW_BASELINE = LocalDate.of(2020, 1, 1);

    @Bean
    InMemoryUserProfileStore userProfileStore() {
        return new InMemoryUserProfileStore();
    }

    @Bean
    InMemoryAccountStore accountStore() {
        return new InMemoryAccountStore();
    }

    @Bean
    ProfileUseCase profileUseCase(InMemoryUserProfileStore store) {
        return new ProfileUseCase(store, POLICY);
    }

    @Bean
    AccountUseCase accountUseCase(InMemoryAccountStore store) {
        return new AccountUseCase(store);
    }

    @Bean
    OAuth2LoginSuccessHandler loginSuccessHandler(InMemoryAccountStore accounts) {
        return new OAuth2LoginSuccessHandler(accounts, "http://localhost:3000");
    }

    /** DB 없이 고정 값을 돌려주는 데이터 현황(웹 슬라이스는 HTTP·인가만 본다). */
    @Bean
    DataStatusStore dataStatusStore() {
        return new DataStatusStore(null) {
            @Override
            public DataStatus read() {
                return new DataStatus(STATUS_UPDATED_AT, 912);
            }
        };
    }

    /** DB 없이 고정 목록·시행예정본 목록(웹 슬라이스는 HTTP·검증·인가만 본다). */
    @Bean
    LawCatalogStore lawCatalogStore() {
        return new LawCatalogStore(null) {
            @Override
            public LawListPage listPending(LocalDate from, LocalDate to, Cursor after, int limit) {
                return new LawListPage(List.of(new LawListItem(
                        "001809", "주택법", LAW_EFFECTIVE, "법률", "국토교통부", "일부개정")), "next-cursor");
            }

            @Override
            public Optional<LawVersions> versions(String lawId) {
                if (!"001809".equals(lawId)) return Optional.empty();
                return Optional.of(new LawVersions("001809", "주택법",
                        List.of(new UpcomingVersion(LAW_EFFECTIVE, "21323", "일부개정")),
                        new InForceVersion(LAW_BASELINE, "20000")));
            }
        };
    }

    /** 주택법 시행예정본(2026-11-01)과 기준선만 있는 메모리 정본. */
    @Bean
    LawSource lawSource() {
        return new LawSource() {
            @Override
            public Optional<Law> find(String lawId, LocalDate effectiveDate) {
                if (!"001809".equals(lawId) || !LAW_EFFECTIVE.equals(effectiveDate)) return Optional.empty();
                return Optional.of(CitedSourceResolverTest.law(Law.Status.시행예정, LAW_EFFECTIVE, "① 통합심의한다."));
            }

            @Override
            public Optional<Law> findBaseline(String lawId) {
                if (!"001809".equals(lawId)) return Optional.empty();
                return Optional.of(CitedSourceResolverTest.law(Law.Status.시행중, LAW_BASELINE, "① 심의한다."));
            }
        };
    }

    /** 001809@2026-11-01 만 있는 조회 집계 — 순위는 고정 1건. */
    @Bean
    LawViewStore lawViewStore() {
        return new LawViewStore(null) {
            @Override
            public boolean record(String lawId, LocalDate effectiveDate) {
                return "001809".equals(lawId) && LAW_EFFECTIVE.equals(effectiveDate);
            }

            @Override
            public TrendingPage trending(TrendingWindow window, int limit) {
                return new TrendingPage(window.name(),
                        List.of(new TrendingItem(1, "001809", "주택법", LAW_EFFECTIVE, 12, null)));
            }
        };
    }

    @Bean
    LawDetailMapper lawDetailMapper() {
        return new LawDetailMapper();
    }
}
