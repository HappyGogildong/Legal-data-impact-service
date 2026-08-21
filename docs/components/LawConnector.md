---
title: LawConnector — 클래스 스펙
status: Draft
date: 2026-08-22
tags: [component, pipeline, connector]
related: ["components/component-specs.md", "components/SourceConnector.md", "reference/law-domain-basics.md", "docs/troubleshooting/004-jejeong-law-no-baseline-english-envelope.md"]
---

# LawConnector

> 국가법령정보(law.go.kr DRF) **MVP 수집 커넥터** — 인증·페이징·응답 기벽을 커넥터 안에 가두고 밖으로는 `RawLaw`만 내보낸다(Anti-Corruption Layer 바깥쪽 절반). 코드: `pipeline/connector/LawConnector`. 도메인: [[law-domain-basics]].

## Responsibility
- **담당:** `eflaw`(시행예정)·`law`(현행) 조회 → `RawLaw`. 목록 페이징, OC 인증, 타임아웃·재시도, 봉투 오류 검사.
- **담당 안 함:** 정규화([[Normalizer]]) · diff([[DiffBuilder]]) · 의안(`RawBill`) 커넥터(post-MVP, [[SourceConnector]]).

## Collaborators
- Spring `RestClient`(타임아웃 요청 팩토리) · `LawEnvelope`(순수 봉투 파싱) · `LiaSourceProperties.Law`(`base·oc·timeout·maxRetries`).
- 계측: `ObservationRegistry`(선택 주입, `NOOP`) — `lia.connector.fetch{target}`.
- 외부 시스템: **국가법령정보 DRF OpenAPI**(OC 인증).

## Contract
- `listPending(from, to, limit) → RawLaw[]` — `efYd` 범위 시행예정 목록(본문 없음). `limit≤0`=전량.
- `searchPending(query, from, to, limit) → RawLaw[]` — 법령명 검색(해소용).
- `fetchPending(mst, efYd) → RawLaw` — 시행예정 본문(`target=eflaw`). 본문 없으면 예외.
- `fetchCurrent(lawId) → RawLaw | null` — 현행 본문(`target=law`). **현행본 없으면 `null`**(제정 = diff 기준선 부재, [[law-domain-basics]] §3).

## External API Contract
| target | 용도 | 조회키 | 본문 루트 |
|---|---|---|---|
| `eflaw` | 시행예정(분석 대상) | `MST` | `법령` |
| `law` | 현행(diff 기준선) | **`ID`**(법령ID; MST 아님) | `법령` / **현행본 없으면 `{"Law":"..."}`** |

- 목록: `GET /DRF/lawSearch.do?target=..&efYd=..&sort=efasc&display=..` → `LawSearch.law[]`
- 본문: `GET /DRF/lawService.do?target=..&(MST|ID)=..` → `법령` 블록

## 봉투·오류 규약
- **인증 실패도 HTTP 200** + `{"result":"..."}` → `checkError`가 봉투로 판별(상태코드 신뢰 불가).
- `display=1`이면 `law`가 배열 아닌 **단일 객체** → `extractRows`가 감싼다(함정1, [[troubleshooting/001-lawapi-display1-single-object|001]]).
- **현행본 없음(제정)** → `{"Law":"일치하는 법령이 없습니다"}` → `hasLawBody=false` → `fetchCurrent`가 `null`([[troubleshooting/004-jejeong-law-no-baseline-english-envelope|004]]).
- 빈 응답 → `LawApiException`.

## Invariants
- 연결키는 **법령ID**(MST는 버전마다 달라 시행중↔시행예정 연결에 못 씀).
- 읽기·연결 **타임아웃 적용**(무한 대기 방지). `LawApiException`(논리 오류)은 재시도 안 함, 5xx/네트워크만 지수 백오프, **마지막 시도 후 백오프 없음**.

## Error Handling
- 봉투 오류·빈 응답 → `LawApiException`(전파). OC 미설정 → `IllegalStateException`. 5xx/네트워크 → 재시도 후 최종 예외.

## Side Effects
- 외부 API **GET(읽기 전용)**. 상태 변경 없음.

## Design Constraints
- OC 자격증명 필수(레포 루트 `.env` 단일 소스, D39). **API 일일 쿼터 존재** — 대량 순회 주의([[troubleshooting/004-jejeong-law-no-baseline-english-envelope|004]] 캐비앗).
