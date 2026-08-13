---
title: 백엔드 동시성·신뢰성 (Backend Concurrency & Reliability)
status: Draft
version: 0.1
date: 2026-08-14
tags: [backend, concurrency, transaction, idempotency, outbox, reliability]
related:
  - "backend/observability.md"
  - "mvp/service-api-spec.md"
  - "adr/decision-log.md"
---

# 백엔드 동시성·신뢰성 (Backend Concurrency & Reliability)

**관련:** [[observability|관측성·측정 환경]] · [[service-api-spec|서비스 API]] · [[decision-log|D48·D49]]

이 문서는 **API 계약이 아닌 내부 백엔드 로직** — 동시성·트랜잭션·신뢰 전송을 다룬다. 격리수준·락·멱등은 사용자에게 노출되지 않으므로 [[service-api-spec]]가 아니라 여기에 둔다.

> **측정 선행(D48).** 아래 각 항목은 *문제 → 신호 → 기법 → 트리거* 순서다. 신호를 관측하기 전에는 기법을 넣지 않는다 — 계기판은 [[observability]]. speculative한 락은 없는 병목을 만든다.

---

## 1. 분석 캐시 스탬피드 — Single-Flight

**문제.** 인기 법령이 **미캐시** 상태에서 N명이 동시에 같은 질의(같은 캐시 키)를 보내면, 같은 Opus 추론이 **N번** 돈다. 비용은 정확히 N배 — ADR-001이 지목한 "런타임 LLM 호출이 비용 동인"과 정면으로 충돌한다.

**신호.** `cache.miss` 급증 + `inflight.duplicate`(동일 키 동시요청).

**기법 — Single-Flight(요청 병합).**
- 캐시 키(`profileHash + law_ref + dimension`)마다 **첫 요청만 계산**하고, 진행 중이면 나머지는 그 결과를 **공유**한다.
- 단일 인스턴스: 인프로세스 `ConcurrentHashMap<Key, CompletableFuture<Result>>`. 계산 시작 시 future 등록, 완료 시 캐시 쓰고 제거.
- 다중 인스턴스: 분산 락(Redis `SET NX PX`) 또는 인스턴스별 허용(소량 over-compute 감수 — 비용 대비 단순성).

**적용 범위.** **Layer B(IMPACT·ACTION)만** — 온디맨드 Opus 호출이라 스탬피드가 생긴다. Layer A(SUMMARY·DIFF)는 오프라인 선계산이라 캐시 조회뿐, 스탬피드 없음.

**트리거.** [[observability]] §5 — 동일 키 동시요청 관측 + 중복 비율 > 5%. **k6 스탬피드 시나리오로 100:100을 먼저 증명**한 뒤 도입, 적용 후 100:1 재측정.

---

## 2. 적재 멱등성·동시성

**문제.** 스케줄러가 `eflaw`를 재수집할 때:
1. 같은 `(lawId, effectiveDate)` **upsert 경합**(동시 실행·재시도)
2. 사용자가 **읽는 중** 배치가 갱신
3. **재실행 중복**(같은 배치 두 번 → 중복·오염)
4. **부분 실패**(한 건 실패가 전체를 롤백)

**기법.**
- **Upsert 원자화:** `INSERT … ON CONFLICT (law_id, effective_date) DO UPDATE …`(Postgres). 유니크 키는 `(lawId, effectiveDate)` — `lawId` 단독 불가(D43, 시행예정본 복수).
- **멱등 — revision CAS:** 새 `revision` 해시가 기존과 같으면 **skip**한다. 불필요한 쓰기·캐시 무효화를 막는다(두 번 돌아도 결과 동일).
- **읽기 중 갱신:** Postgres MVCC 기본(Read Committed)로 읽기는 스냅샷을 본다. 분석은 특정 `(lawId, efYd, revision)`를 **고정 참조**하므로, 진행 중 분석은 갱신과 무관하게 일관된다.
- **격리수준:** 기본 **Read Committed로 충분**하다. Serializable/명시 락은 `upsert.conflict` **실측 후에만** — 예단하지 않는다(D48).
- **부분 실패:** 배치를 **법령 단위 트랜잭션**으로 쪼갠다. 한 건 실패가 나머지를 롤백하지 않고, 실패분만 다음 주기에 재시도.

**신호.** `ingest.upsert.conflict`·`ingest.duration`·이상 데이터.

**트리거.** upsert는 처음부터(원자성 기본). 격리수준 상향은 경합 실측 시.

---

## 3. 시행 임박 알림 — Outbox · 정확히 한 번

**문제.** 시행일 도래/임박(D-N) 배치가 구독자에게 **팬아웃** 통지한다. 중복·누락 없이(**정확히 한 번**), 도메인 트랜잭션과 **원자적**으로.

**⚠️ D41 긴장 — 연락처를 수집하지 않는다(D49).** 우리는 이메일·전화를 받지 않으므로 외부 발송이 불가하다. **MVP는 인앱 알림함**(로그인 시 표시, PII 불필요). 외부 채널(이메일 등)은 **명시적 별도 동의 opt-in**으로만(post-MVP) — D41 최소수집 원칙 유지.

**기법.**
- **Outbox 패턴:** 알림 이벤트를 도메인 변경(시행일 전이·분석)과 **같은 DB 트랜잭션**에 `outbox` 테이블로 커밋 → 별도 릴레이가 인앱 알림함에 적재. "상태는 바뀌었는데 알림은 유실" 을 원천 차단.
- **정확히 한 번:** dedup 키 = `(subscriptionId, lawRef, eventType)` 유니크 제약. 중복 삽입 무시.
- **팬아웃:** 시행일 배치가 구독 매칭 → outbox insert(대량이면 페이징). at-least-once 전송 + **멱등 소비** = 실무상 effectively-once.

**신호.** `notify.delivery.lag`·`notify.duplicate`(> 0이면 정확히 한 번 위반).

**트리거.** 구독 기능 도입 시 **outbox부터**(신뢰성 기본값). 여기선 예단이 아니라 정확성 요건이다.

---

## 4. 감사 로그 — 그라운딩 책임성

**문제.** 법률 정보 서비스는 사후에 *"이 답이 무슨 근거였나"* 를 재현·설명해야 한다(분쟁·규제 대응). 휘발성 운영 로그로는 부족하다.

**기법.** 답변마다 **append-only 감사 레코드**를 남긴다:
```
{ answerId, lawRef, citations[source_id], model, promptVersion, ts }
```
- 운영 로그(Loki, TTL 휘발)와 **분리** — 영구 보존.
- 저장: RDS append-only 테이블. 쓰기 집약이면 **월 단위 파티셔닝** + 보존정책.
- 그라운딩(D08)과 직결 — 모든 주장이 조문 `source_id`로 역추적됨을 기록으로 증명.

**⚠️ D41.** 인용·근거만 남기고 `userId` 대신 **프로필 해시**. 질의 원문은 감사에 넣지 않거나 마스킹. 로그·감사가 최소수집의 뒷문이 되지 않게(=[[observability]] §4).

**트리거.** 규제·분쟁 요건. MVP는 최소 필드부터, 보존·파티셔닝은 볼륨 실측 후.

---

## 5. 공통 원칙

- **읽기 경로는 락을 피한다.** 분석은 불변 스냅샷(`revision` 고정)을 참조 — 쓰기(적재·전이)와 읽기(분석)를 시점으로 분리한다(오프라인/온라인 모드 분리, v0.8~v0.9와 정합).
- **캐시 무효화는 키 설계로.** 프로필 변경 시 Layer B 캐시는 **프로필 해시가 바뀌어 자연스레 miss**된다 — 명시적 무효화 불필요(D41 캐시 키 설계의 부수 이득).
- **각 기법은 관측 뒤에.** 이 문서의 어떤 항목도 [[observability]] 계기판 없이 먼저 구현하지 않는다.
