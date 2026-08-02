---
title: SourceAnalyzer — 컴포넌트 설계
status: Draft
version: 0.2
date: 2026-08-02
tags: [component, pipeline, resolver]
related: ["components/component-specs.md", "architecture/v0.8-pending-law-corpus.md"]
---

# SourceAnalyzer (Spring, 식별)

> **v0.1 → v0.2 (D42, 2026-08-02):** 해소 대상이 *의안* → **시행예정 법령**으로 바뀌었다. **의안번호 분기 제거**(법령에 의안번호가 없고 사용자가 법령ID `001809` 를 입력하지도 않는다), **커넥터 의존 제거**(`LawLookup` 포트 경유). 런타임은 Spring(D35).

> 사용자 입력 → **어떤 시행예정 법령인가**를 해소(resolve). 분석가가 아니라 *식별자(resolver)*. 관련: [[component-specs]] §4 #2 · [[v0.8-pending-law-corpus]] §3.2

## 역할

법령명 또는 **모호한 자연어**를 받아 실재하는 시행예정 법령으로 해소한다. **신뢰 출처에서 확인되지 않으면 분석하지 않는다(fail-closed)** — 입력 *내용*을 사실로 받지 않는다.

## 입력 / 출력

| | 타입 | 설명 |
|---|---|---|
| 입력 | `String` (법령명 / 자연어 서술) | URL·기사 본문은 확장점(스텁) |
| 출력 | `ResolutionResult{ state, resolved?\|candidates?\|similar?, message? }` | 4상태 |

해소 4상태: `RESOLVED` / `AMBIGUOUS` / `NOT_FOUND_YET` / `UNVERIFIED`.

> **불변식은 타입이 강제한다.** `RESOLVED` 가 아니면 `resolved` 는 반드시 `null` 이고, 미해소 상태는 안내 문구가 필수다. 규율이 문서와 관례에만 있으면 언젠가 우회되므로 생성자에서 막는다(D23).

## 파라미터 (설정)

| 파라미터 | 기본 | 설명 |
|---|---|---|
| `confident` | 88.0 | 단정(RESOLVED) 임계 — 토큰 유사도 |
| `ambiguous-min` | 60.0 | 후보 채택 최소 유사도 |
| `lookahead-years` | 2 | 훑을 시행예정 범위(오늘 ~ N년) |
| `semantic-top-k` | 5 | 의미검색 후보 수 |

## 동작

1. **법령명 정확·퍼지 매칭** — `LawLookup.searchByName` → 토큰 유사도 정렬
   - 단일 강매칭 → `RESOLVED`
   - 강매칭 복수 → `AMBIGUOUS`. **같은 `법령ID` 뿐이면 "어느 시행일 기준인지" 되묻는다**(D43)
   - 약매칭만 → `AMBIGUOUS`(후보 제시)
2. **의미검색** — 1이 실패하면 탐색용 네임스페이스(`pending`, [[RAGIndexer]])로 후보화. 모호 서술("집 구할 때 뭔가 바뀐다던데")을 커버
3. **fail-closed 판정** — 여기까지 못 찾으면
   - 법령스러운 표현이면 → `NOT_FOUND_YET` ("아직 공포되지 않았거나, 이미 시행 중이어서 분석 대상이 아닐 수 있습니다")
   - 아니면 → `UNVERIFIED` ("확인되지 않은 정보입니다")

## 인터페이스 (Java, `com.lia.core.pipeline.resolve`)

```java
public class SourceAnalyzer {
    ResolutionResult resolve(String input);
}

public interface LawLookup {                       // 아웃바운드 포트
    List<RawLaw> searchByName(String query, int limit);
}
```

> **포트를 두는 이유.** [[v0.8-pending-law-corpus|v0.8]] §3.2에서 해소는 **Law Store·Vector Index(오프라인 적재분)** 를 읽는 것이지 출처 API를 직접 부르는 것이 아니다. 저장소가 없는 지금은 `LawConnector` 를 어댑터로 꽂아 두고, 적재가 끝나면 **구현만 교체한다**(`SourceAnalyzer` 무수정).

## 구조 결정 의도 (왜 이렇게)

- **resolver ≠ analyzer.** "어떤 법령인가"만 판정하고, 데이터는 항상 신뢰 출처 원문에서 온다 → 뉴스·소문이 분석으로 둔갑하지 않는다(그라운딩).
- **fail-closed.** 확인 안 되면 분석을 거부한다. 4상태로 **미등록(`NOT_FOUND_YET`)과 허위 의심(`UNVERIFIED`)을 구분**해 안내 문구를 다르게 한다([[decision-log|D23]]) — "아직 없는 법"과 "지어낸 법"은 사용자에게 전혀 다른 이야기다.
- **정확매칭 + 의미검색 2단계.** 모호 plain text는 정확매칭이 안 되므로 탐색용 임베딩으로 후보화한다([[decision-log|D30]]).
- **같은 법령의 복수 시행예정본은 단정하지 않는다(D43).** 실측에서 자본시장법이 3건(2026-10-01·11-13, 2027-02-04)이었다. 어느 시점 기준인지 임의로 고르면 사용자에게 틀린 시행일을 준다.
- **출처 장애도 fail-closed로 떨어진다.** 조회가 예외를 던지면 빈 결과로 처리해 `NOT_FOUND_YET`/`UNVERIFIED` 로 간다 — 장애가 사용자에게 스택트레이스로 새지 않고, 없는 결과를 지어내지도 않는다.

## 의존 / 관련

- 의존: `LawLookup` 포트 → (현재) [[SourceConnector|LawConnector]] · (예정) Law Store + Vector Index `pending` ns
- 게이트 소비: AnalysisPipeline(#8) 0단계 — `RESOLVED` 만 통과
