package com.lia.core.application.profile;

import java.util.Optional;
import java.util.UUID;

import com.lia.core.application.account.AccountNotFoundException;
import com.lia.core.profile.StoredProfile;
import com.lia.core.profile.UserProfile;
import com.lia.core.profile.UserProfileStore;

/**
 * 프로필·프로필 동의 유스케이스(application 계층, D61) — {@code user_profiles} 한 행을 둘러싼 규칙.
 *
 * <p>동의가 먼저(만 14세 확인 + 현재 처리방침 버전 기록), 수정은 현재 버전 동의가 있을 때만.
 * 클라이언트는 버전을 보내지 않는다 — 서버 설정값을 찍는다. Spring 애노테이션 없음:
 * {@code config/UserConfig} 의 {@code @Bean} 이 배선한다. 설계: docs/components/application/ProfileUseCase.md
 */
public class ProfileUseCase {

    private final UserProfileStore store;
    private final String currentPolicyVersion;

    public ProfileUseCase(UserProfileStore store, String currentPolicyVersion) {
        this.store = store;
        this.currentPolicyVersion = currentPolicyVersion;
    }

    /**
     * 프로필 수집 동의 — 행이 없으면 빈 프로필 생성, 있으면 버전·일시만 갱신. 재동의도 같은 호출.
     *
     * @throws AccountNotFoundException 세션이 가리키는 계정이 없음(다른 기기에서 계정 삭제 등) — web 이 401 로 매핑
     */
    public ProfileConsent agree(UUID userId, boolean over14) {
        if (!over14) {
            throw new IllegalArgumentException("만 14세 이상만 이용할 수 있습니다(over14=true 필요).");
        }
        if (!store.recordConsent(userId, currentPolicyVersion)) {
            throw new AccountNotFoundException();
        }
        return consentStatus(userId).orElseThrow(AccountNotFoundException::new);
    }

    public Optional<ProfileConsent> consentStatus(UUID userId) {
        return store.find(userId).map(stored -> new ProfileConsent(
                stored.policyVersion(), stored.consentedAt(), currentPolicyVersion.equals(stored.policyVersion())));
    }

    /** 속성 전체 교체. 현재 버전 동의가 없으면(없음·옛 버전·그사이 삭제) ConsentRequiredException. */
    public StoredProfile update(UUID userId, UserProfile profile) {
        boolean consented = consentStatus(userId).map(ProfileConsent::upToDate).orElse(false);
        if (!consented || !store.replaceAttributes(userId, profile)) {
            throw new ConsentRequiredException();
        }
        return store.find(userId).orElseThrow(ConsentRequiredException::new);
    }

    public Optional<StoredProfile> find(UUID userId) {
        return store.find(userId);
    }

    /** 파기 = 프로필 동의 철회. 멱등. */
    public void delete(UUID userId) {
        store.delete(userId);
    }
}
