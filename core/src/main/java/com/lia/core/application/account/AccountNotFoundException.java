package com.lia.core.application.account;

/**
 * 세션이 가리키는 {@code userId} 의 계정이 이미 없음(다른 기기에서 계정 삭제 후 등) — web 이 401 로 매핑(D61).
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException() {
        super("세션이 가리키는 계정이 없습니다. 다시 로그인하세요.");
    }
}
