package com.lia.core.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lia.core.application.history.AnalysisExpiredException;
import com.lia.core.application.history.HistoryUseCase;
import com.lia.core.application.history.SavedHistory;
import com.lia.core.auth.CurrentUser;
import com.lia.core.store.AnalysisHistoryStore.HistoryEntry;
import com.lia.core.store.AnalysisHistoryStore.NewEntry;
import com.lia.core.web.HistoryApiDtos.HistoryDetailResponse;
import com.lia.core.web.HistoryApiDtos.HistoryItem;
import com.lia.core.web.HistoryApiDtos.HistoryListResponse;
import com.lia.core.web.HistoryApiDtos.HistorySaveRequest;
import com.lia.core.web.HistoryApiDtos.HistorySavedResponse;
import com.lia.core.web.HistoryApiDtos.HistorySnapshot;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code /api/v1/history} — 질의·분석 이력(D73, [[service-api-spec]] §3.9). 세션에 보관한 분석 결과로 스냅샷 JSON 을 만들어
 * {@link HistoryUseCase} 에 넘기고, 상세 때 다시 읽는다. 404·409·401 은 ApiExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

    private final HistoryUseCase history;
    private final ObjectMapper json;

    public HistoryController(HistoryUseCase history, ObjectMapper json) {
        this.history = history;
        this.json = json;
    }

    /** 새로 저장하면 201, 같은 분석을 이미 저장했으면 200 — 본문은 같다. */
    @PostMapping
    public ResponseEntity<HistorySavedResponse> save(@RequestBody HistorySaveRequest body, HttpServletRequest request) {
        if (body == null || body.analysisId() == null || body.analysisId().isBlank()) {
            throw new IllegalArgumentException("analysisId는 필수입니다.");
        }
        UUID userId = CurrentUser.require(request);
        HttpSessionRecentAnalyses.Entry entry = new HttpSessionRecentAnalyses(request.getSession(false))
                .find(body.analysisId()).orElseThrow(AnalysisExpiredException::new);
        String snapshot = json.writeValueAsString(new HistorySnapshot(entry.body(), entry.revision(), entry.answers()));
        String title = entry.body().law() != null && entry.body().law().title() != null
                ? entry.body().law().title() : "법령 " + entry.lawRef().lawId();
        SavedHistory saved = history.save(userId, new NewEntry(body.analysisId(), entry.query(), entry.lawRef().lawId(),
                entry.lawRef().effectiveDate(), title, snapshot));
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new HistorySavedResponse(saved.id()));
    }

    @GetMapping
    public HistoryListResponse list(HttpServletRequest request) {
        return new HistoryListResponse(history.list(CurrentUser.require(request)).stream().map(HistoryItem::from).toList());
    }

    @GetMapping("/{id}")
    public HistoryDetailResponse detail(@PathVariable long id, HttpServletRequest request) {
        HistoryEntry entry = history.find(CurrentUser.require(request), id);
        HistorySnapshot snapshot = json.readValue(entry.snapshotJson(), HistorySnapshot.class);
        HistoryItem item = HistoryItem.from(entry.row());
        return new HistoryDetailResponse(item.id(), item.query(), item.lawId(), item.lawTitle(), item.effectiveDate(),
                item.savedAt(), item.lawUpdated(), snapshot.body(), snapshot.revision(),
                snapshot.answers() == null ? List.of() : snapshot.answers(), entry.latestEffectiveDate());
    }

    /** 멱등 — 없거나 남의 것이어도 204. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable long id, HttpServletRequest request) {
        history.delete(CurrentUser.require(request), id);
        return ResponseEntity.noContent().build();
    }
}
