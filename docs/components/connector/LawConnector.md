---
title: LawConnector — 클래스 스펙
status: Draft
date: 2026-08-22
tags: [component, pipeline, connector]
related: ["components/component-specs.md", "components/connector/SourceConnector.md", "reference/law-domain-basics.md", "docs/troubleshooting/004-jejeong-law-no-baseline-english-envelope.md", "docs/troubleshooting/005-current-law-by-promulgation-date.md"]
---

# LawConnector

> 국가법령정보(law.go.kr DRF) **MVP 수집 커넥터** — 인증·페이징·응답 기벽을 커넥터 안에 가두고 밖으로는 `RawLaw`만 내보낸다(Anti-Corruption Layer 바깥쪽 절반). 코드: `pipeline/connector/LawConnector`. 도메인: [[law-domain-basics]].

## Responsibility
- **담당:** `eflaw` 로 시행예정본과 현행본(시행일 기준) 조회 → `RawLaw`. 목록 페이징, OC 인증, 타임아웃·재시도, 봉투 오류 검사.
- **담당 안 함:** 정규화([[Normalizer]]) · diff([[DiffBuilder]]) · 의안(`RawBill`) 커넥터(post-MVP, [[SourceConnector]]).

## Collaborators
- Spring `RestClient`(타임아웃 요청 팩토리) · `LawEnvelope`(순수 봉투 파싱) · `LiaSourceProperties.Law`(`base·oc·timeout·maxRetries`).
- 계측: `ObservationRegistry`(선택 주입, `NOOP`) — `lia.connector.fetch{target}`.
- 외부 시스템: **국가법령정보 DRF OpenAPI**(OC 인증).

## Contract
- `listPending(from, to, limit) → RawLaw[]` — `efYd` 범위 시행예정 목록(본문 없음). `limit≤0`=전량.
- `searchPending(query, from, to, limit) → RawLaw[]` — 법령명 검색(해소용).
- `fetchPending(mst, efYd) → RawLaw` — 시행예정 본문(`target=eflaw`). 본문 없으면 예외.
- `fetchCurrent(lawId) → RawLaw | null` — **시행일 기준** 현행 본문(D76). 목록 `target=eflaw&LID={lawId}&nw=3` 으로 현행 행을 찾고, 그 행의 `법령일련번호`·`시행일자`로 `fetchPending` 과 같은 본문 경로를 부른다(호출 2회, 상태 `시행중`). **현행 행이 0건이면 `null`**(제정 = diff 기준선 부재, [[law-domain-basics]] §3) — 본문은 부르지 않는다.

## External API Contract
| target | 용도 | 조회키 | 본문 루트 |
|---|---|---|---|
| `eflaw` | 시행예정(분석 대상) | `MST` | `법령` |
| `eflaw` 목록 `nw=3` | 현행 행 찾기(diff 기준선) | **`LID`**(법령ID; MST 아님) | `LawSearch` — 현행 1행, 제정이면 `totalCnt=0` |
| `eflaw` 본문 | 현행 본문(diff 기준선) | 현행 행의 `MST`+`efYd` | `법령` |

> **`target=law` 는 쓰지 않는다(D76).** API 안내서상 "현행법령(**공포일**)"이라, 단계적 시행 법령이면 일부만 시행 중인 공포본을 **마지막 시행일 기준 본문**으로 준다(아직 시행되지 않은 조문 포함). `eflaw&ID=`(efYd 생략) 단독 조회는 제정 법령에 HTML "미신청된 목록/본문" 페이지를 줘 권한 오류와 구분되지 않는다([[troubleshooting/005-current-law-by-promulgation-date|005]]).

- 목록: `GET /DRF/lawSearch.do?target=eflaw&efYd=..&sort=efasc&display=..` → `LawSearch.law[]`
- 현행 행: `GET /DRF/lawSearch.do?target=eflaw&LID=..&nw=3` → `LawSearch.law` 0~1건
- 본문: `GET /DRF/lawService.do?target=eflaw&MST=..&efYd=..` → `법령` 블록

## 봉투·오류 규약
- **인증 실패도 HTTP 200** + `{"result":"..."}` → `checkError`가 봉투로 판별(상태코드 신뢰 불가).
- `display=1`이면 `law`가 배열 아닌 **단일 객체** → `extractRows`가 감싼다(함정1, [[troubleshooting/001-lawapi-display1-single-object|001]]).
- **현행본 없음(제정)** → 현행 목록 `totalCnt=0` → `fetchCurrent`가 `null`([[troubleshooting/004-jejeong-law-no-baseline-english-envelope|004]] — 당시 `target=law` 의 `{"Law":"..."}` 봉투로 판별하던 것을 D76 이 대체).
- **현행 행이 2건 이상**이거나 **현행 본문의 `시행일자`가 행의 `시행일자`와 다르면** `LawApiException`(추측해 고르지 않는다, D76).
- 빈 응답 → `LawApiException`.

## Invariants
- 연결키는 **법령ID**(MST는 버전마다 달라 시행중↔시행예정 연결에 못 씀). 단계적 시행이면 한 MST 에 시행일별 버전이 여럿이라 **본문은 MST+시행일로** 특정한다.
- `fetchCurrent` 가 돌려주는 본문의 시행일은 API 가 `현행` 으로 표시한 행의 시행일과 같다(시행일 기준 현행, D43 ②·D76).
- 읽기·연결 **타임아웃 적용**(무한 대기 방지). `LawApiException`(논리 오류)은 재시도 안 함, 5xx/네트워크만 지수 백오프, **마지막 시도 후 백오프 없음**.

## Error Handling
- 봉투 오류·빈 응답 → `LawApiException`(전파). OC 미설정 → `IllegalStateException`. 5xx/네트워크 → 재시도 후 최종 예외.

## Side Effects
- 외부 API **GET(읽기 전용)**. 상태 변경 없음.

## Design Constraints
- OC 자격증명 필수(레포 루트 `.env` 단일 소스, D39). **API 일일 쿼터 존재** — 대량 순회 주의([[troubleshooting/004-jejeong-law-no-baseline-english-envelope|004]] 캐비앗).
