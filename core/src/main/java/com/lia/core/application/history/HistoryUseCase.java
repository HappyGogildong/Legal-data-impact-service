package com.lia.core.application.history;

import java.util.List;
import java.util.UUID;

import com.lia.core.application.account.AccountNotFoundException;
import com.lia.core.store.AnalysisHistoryStore;
import com.lia.core.store.AnalysisHistoryStore.HistoryEntry;
import com.lia.core.store.AnalysisHistoryStore.HistoryRow;
import com.lia.core.store.AnalysisHistoryStore.NewEntry;

/**
 * 질의·분석 이력 유스케이스(D73) — 저장소 결과를 예외로 바꾼다. 스냅샷은 web 이 만든 불투명한 JSON 문자열.
 * Spring 애노테이션 없음: {@code config/UserConfig} 의 {@code @Bean}. 설계: docs/components/web/HistoryApi.md
 */
public class HistoryUseCase {

    private final AnalysisHistoryStore store;

    public HistoryUseCase(AnalysisHistoryStore store) {
        this.store = store;
    }

    /**
     * @throws AnalysisExpiredException 그 법령 버전이 없어짐
     * @throws HistoryLimitException    한도 초과
     * @throws AccountNotFoundException 세션이 가리키는 계정이 없음 — web 이 401 로 매핑
     */
    public SavedHistory save(UUID userId, NewEntry entry) {
        var result = store.save(userId, entry);
        return switch (result.status()) {
            case SAVED -> new SavedHistory(result.id(), true);
            case ALREADY -> new SavedHistory(result.id(), false);
            case LAW_NOT_FOUND -> throw new AnalysisExpiredException();
            case LIMIT -> throw new HistoryLimitException(store.maxPerUser());
            case ACCOUNT_NOT_FOUND -> throw new AccountNotFoundException();
        };
    }

    /** 저장 최근 순. */
    public List<HistoryRow> list(UUID userId) {
        return store.list(userId);
    }

    /** @throws HistoryNotFoundException 없거나 남의 기록 */
    public HistoryEntry find(UUID userId, long id) {
        return store.find(userId, id).orElseThrow(() -> new HistoryNotFoundException(id));
    }

    /** 멱등. */
    public void delete(UUID userId, long id) {
        store.delete(userId, id);
    }
}
