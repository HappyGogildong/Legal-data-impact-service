package com.lia.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.lia.core.application.account.AccountUseCase;
import com.lia.core.application.bookmark.BookmarkUseCase;
import com.lia.core.application.history.HistoryUseCase;
import com.lia.core.application.profile.PersonalizationGate;
import com.lia.core.application.profile.ProfileUseCase;
import com.lia.core.auth.AccountStore;
import com.lia.core.profile.UserProfileStore;
import com.lia.core.store.AnalysisHistoryStore;
import com.lia.core.store.LawBookmarkStore;

/**
 * 사용자(계정·프로필·북마크·이력) 유스케이스 배선 — application 코어는 Spring 애노테이션 없이 두고 여기서 {@code @Bean} 으로 잇는다
 * ({@link PipelineConfig} 의 AnalyzeUseCase 와 같은 방식).
 */
@Configuration
public class UserConfig {

    @Bean
    public ProfileUseCase profileUseCase(UserProfileStore store,
            @Value("${lia.privacy.policy-version}") String policyVersion) {
        return new ProfileUseCase(store, policyVersion);
    }

    @Bean
    public PersonalizationGate personalizationGate(UserProfileStore store,
            @Value("${lia.privacy.policy-version}") String policyVersion) {
        return new PersonalizationGate(store, policyVersion);
    }

    @Bean
    public AccountUseCase accountUseCase(AccountStore store) {
        return new AccountUseCase(store);
    }

    @Bean
    public LawBookmarkStore lawBookmarkStore(JdbcClient jdbcClient, PlatformTransactionManager transactionManager,
            @Value("${lia.bookmarks.max-per-user}") int maxPerUser) {
        return new LawBookmarkStore(jdbcClient, new TransactionTemplate(transactionManager), maxPerUser);
    }

    @Bean
    public BookmarkUseCase bookmarkUseCase(LawBookmarkStore store) {
        return new BookmarkUseCase(store);
    }

    @Bean
    public AnalysisHistoryStore analysisHistoryStore(JdbcClient jdbcClient, PlatformTransactionManager transactionManager,
            @Value("${lia.history.max-per-user}") int maxPerUser) {
        return new AnalysisHistoryStore(jdbcClient, new TransactionTemplate(transactionManager), maxPerUser);
    }

    @Bean
    public HistoryUseCase historyUseCase(AnalysisHistoryStore store) {
        return new HistoryUseCase(store);
    }
}
