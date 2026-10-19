package com.lia.core.testsupport;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.lia.core.pipeline.connector.LawApiException;
import com.lia.core.pipeline.connector.PendingLawFeed;
import com.lia.core.pipeline.connector.RawLaw;

/** 가짜 법령 원천 — 목록·본문·기준선을 테스트가 정하고, 목록 실패·본문 fetch 실패를 주입한다. */
public class FakeLawFeed implements PendingLawFeed {

    private final List<RawLaw> heads = new ArrayList<>();
    private final Map<String, RawLaw> pendingByMst = new HashMap<>();
    private final Map<String, RawLaw> currentByLawId = new HashMap<>();
    private final Map<String, RuntimeException> fetchFailures = new HashMap<>();
    private boolean listComplete = true;
    private RuntimeException listFailure;
    private final AtomicInteger fetchCount = new AtomicInteger();

    public FakeLawFeed head(String lawId, String mst, LocalDate effectiveDate) {
        heads.add(new RawLaw(lawId, mst, null, "시행예정", effectiveDate, null, null, null));
        return this;
    }

    public FakeLawFeed clearHeads() {
        heads.clear();
        return this;
    }

    public FakeLawFeed listComplete(boolean complete) {
        this.listComplete = complete;
        return this;
    }

    public FakeLawFeed failList(RuntimeException failure) {
        this.listFailure = failure;
        return this;
    }

    public FakeLawFeed pendingBody(String mst, RawLaw raw) {
        pendingByMst.put(mst, raw);
        return this;
    }

    public FakeLawFeed currentBody(String lawId, RawLaw raw) {
        currentByLawId.put(lawId, raw);
        return this;
    }

    /** 이 MST 의 본문 fetch 가 항상 이 예외로 실패한다. null 이면 해제. */
    public FakeLawFeed failFetch(String mst, RuntimeException failure) {
        if (failure == null) fetchFailures.remove(mst); else fetchFailures.put(mst, failure);
        return this;
    }

    /** 본문(fetchPending) 호출 횟수. */
    public int fetchCount() {
        return fetchCount.get();
    }

    /** 주택법 fixture 를 표준 구성으로 등록(목록 행 + 본문 + 기준선). */
    public FakeLawFeed withHousingAct() {
        return head(LawFixtures.HOUSING_LAW_ID, LawFixtures.HOUSING_MST, LawFixtures.HOUSING_EFFECTIVE)
                .pendingBody(LawFixtures.HOUSING_MST, LawFixtures.raw(LawFixtures.HOUSING_PENDING, "시행예정"))
                .currentBody(LawFixtures.HOUSING_LAW_ID, LawFixtures.raw(LawFixtures.HOUSING_BASELINE, "시행중"));
    }

    @Override
    public PendingList listAllPending(LocalDate from, LocalDate to) {
        if (listFailure != null) throw listFailure;
        List<RawLaw> inRange = heads.stream()
                .filter(h -> !h.effectiveDate().isBefore(from) && !h.effectiveDate().isAfter(to))
                .toList();
        return new PendingList(inRange, listComplete);
    }

    @Override
    public RawLaw fetchPending(String mst, LocalDate effectiveDate) {
        fetchCount.incrementAndGet();
        RuntimeException failure = fetchFailures.get(mst);
        if (failure != null) throw failure;
        RawLaw raw = pendingByMst.get(mst);
        if (raw == null) throw new LawApiException("없는 MST: " + mst);
        return raw;
    }

    @Override
    public RawLaw fetchCurrent(String lawId) {
        return currentByLawId.get(lawId);
    }
}
