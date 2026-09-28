package com.lia.core.application.account;

import java.util.Optional;
import java.util.UUID;

import com.lia.core.auth.Account;
import com.lia.core.auth.AccountStore;

/**
 * 계정·알림 이메일 동의 유스케이스(application 계층, D61) — {@code accounts} 한 행을 둘러싼 규칙.
 *
 * <p>이메일을 어디서 얻는지(세션 principal)는 web 의 일이다 — 여기는 "이메일이 있어야 동의 가능"만 판단한다.
 * Spring 애노테이션 없음: {@code config/UserConfig} 의 {@code @Bean}. 설계: docs/components/application/AccountUseCase.md
 */
public class AccountUseCase {

    private final AccountStore store;

    public AccountUseCase(AccountStore store) {
        this.store = store;
    }

    public Optional<Account> find(UUID userId) {
        return store.find(userId);
    }

    /** 계정 파기 — 프로필은 FK cascade 로 함께 삭제(D41). 되돌릴 수 없다. */
    public void delete(UUID userId) {
        store.delete(userId);
    }

    /**
     * 알림 이메일 수신 동의. IdP 가 이메일을 주지 않았으면(미제공·미동의) IllegalArgumentException.
     *
     * @throws AccountNotFoundException 세션이 가리키는 계정이 없음(다른 기기에서 계정 삭제 등) — web 이 401 로 매핑
     */
    public Account agreeNotificationEmail(UUID userId, String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "로그인한 계정에서 이메일을 받을 수 없습니다. 소셜 로그인의 이메일 제공 동의를 확인하세요.");
        }
        if (!store.setNotificationEmail(userId, email)) {
            throw new AccountNotFoundException();
        }
        return store.find(userId).orElseThrow(AccountNotFoundException::new);
    }

    /** 알림 동의 철회. 멱등. */
    public void withdrawNotificationEmail(UUID userId) {
        store.clearNotificationEmail(userId);
    }
}
