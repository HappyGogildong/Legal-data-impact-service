package com.lia.core.application.profile;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lia.core.application.account.AccountNotFoundException;
import com.lia.core.profile.Occupation;
import com.lia.core.profile.Purpose;
import com.lia.core.profile.UserProfile;
import com.lia.core.testsupport.InMemoryUserProfileStore;

/** ProfileUseCase — 동의가 먼저, 현재 버전 동의가 있을 때만 수정, 버전 기반 재동의. */
class ProfileUseCaseTest {

    static final String CURRENT = "v2";
    static final UserProfile PROFILE = new UserProfile(Set.of(Purpose.생활주거), 29, Occupation.사무, null, null, null, null);

    final InMemoryUserProfileStore store = new InMemoryUserProfileStore();
    final ProfileUseCase useCase = new ProfileUseCase(store, CURRENT);
    final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("over14=false 동의 → IllegalArgumentException, 아무것도 기록되지 않음")
    void 동의_14세미확인_거부() {
        assertThrows(IllegalArgumentException.class, () -> useCase.agree(userId, false));
        assertTrue(store.find(userId).isEmpty());
    }

    @Test
    @DisplayName("동의 → 현재 버전으로 기록, upToDate, 빈 프로필")
    void 동의() {
        ProfileConsent consent = useCase.agree(userId, true);

        assertEquals(CURRENT, consent.policyVersion());
        assertTrue(consent.upToDate());
        assertEquals(UserProfile.empty(), useCase.find(userId).orElseThrow().profile());
    }

    @Test
    @DisplayName("동의 전 수정 → ConsentRequiredException")
    void 동의전_수정거부() {
        assertThrows(ConsentRequiredException.class, () -> useCase.update(userId, PROFILE));
        assertTrue(store.find(userId).isEmpty());
    }

    @Test
    @DisplayName("동의 후 수정 → 저장된 프로필 반환, 동의 불변")
    void 동의후_수정() {
        ProfileConsent consent = useCase.agree(userId, true);

        var stored = useCase.update(userId, PROFILE);

        assertEquals(PROFILE, stored.profile());
        assertEquals(consent.consentedAt(), stored.consentedAt());
    }

    @Test
    @DisplayName("옛 버전 동의 → upToDate=false, 수정 거부 → 재동의 후 수정 가능")
    void 버전상승_재동의() {
        store.recordConsent(userId, "v1");   // 처리방침 개정 전 동의

        assertFalse(useCase.consentStatus(userId).orElseThrow().upToDate());
        assertThrows(ConsentRequiredException.class, () -> useCase.update(userId, PROFILE));

        useCase.agree(userId, true);

        assertTrue(useCase.consentStatus(userId).orElseThrow().upToDate());
        assertEquals(PROFILE, useCase.update(userId, PROFILE).profile());
    }

    @Test
    @DisplayName("동의 상태 — 동의 없으면 empty")
    void 동의상태_없음() {
        assertTrue(useCase.consentStatus(userId).isEmpty());
    }

    @Test
    @DisplayName("recordConsent 가 false(계정 없음) → agree 가 AccountNotFoundException")
    void 동의_계정없음_401매핑() {
        var storeWithoutAccount = new InMemoryUserProfileStore() {
            @Override
            public boolean recordConsent(UUID userId, String policyVersion) {
                return false;
            }
        };
        var useCase = new ProfileUseCase(storeWithoutAccount, CURRENT);

        assertThrows(AccountNotFoundException.class, () -> useCase.agree(userId, true));
    }

    @Test
    @DisplayName("처리방침 버전이 공백/빈 값이면 생성자에서 IllegalArgumentException(부팅 시 fail-fast)")
    void 생성자_처리방침버전_공백거부() {
        assertThrows(IllegalArgumentException.class, () -> new ProfileUseCase(store, " "));
        assertThrows(IllegalArgumentException.class, () -> new ProfileUseCase(store, ""));
        assertThrows(IllegalArgumentException.class, () -> new ProfileUseCase(store, null));
    }

    @Test
    @DisplayName("delete → 파기(동의도 함께 사라짐)")
    void 파기() {
        useCase.agree(userId, true);

        useCase.delete(userId);

        assertTrue(useCase.find(userId).isEmpty());
        assertTrue(useCase.consentStatus(userId).isEmpty());
    }
}
