---
title: "006 · 현행본이 미래본 — target=law 는 공포일 기준 현행"
status: Resolved
date: 2026-11-01
tags: [troubleshooting, connector, api, domain, date-dependent]
---

# 006 · 현행본이 미래본 — `target=law` 는 공포일 기준 현행

| | |
|---|---|
| 유형 | 커넥터 · API 의미 · 도메인 상태 · **날짜 의존** |
| 발생 시점 | 2026-11-01 (청킹 근거용으로 현행본 원본을 받다가 발견 — [[005-article-text-number-duplication|005]] · 로컬 적재 데이터 점검) |
| 관련 | D43 ② · D76 · 커밋 `02b6e24`(정책) · `eaa3d75`(실패 테스트) · `b0e41a6`(수정) · `LawConnector.fetchCurrent` · `IngestService.store` · `SyncHandler` · 원본 `docs/mvp/raw-samples/law-001809-current-raw.json.txt` |

## 1. 문제 정의

diff 기준선(D43 ② "현재 시행중본")으로 받은 주택법(법령ID 001809) 현행본의 `기본정보.시행일자`가 **20270309** 로 오늘(2026-11-01)보다 뒤였다. 로컬 `law_versions` 에도 `001809 · 시행중 · 2027-03-09` 행이 있고, 시행예정본 2027-09-09 의 diff 기준선으로 쓰였다.

조용한 실패다 — 예외 없이 저장되고, 화면은 "현행 대비"를 정상처럼 보여 준다.

## 2. 가설

1. `target=law` 가 "현재 시행 중인 본"이 아니라 **최신 공포본**(시행일이 미래일 수 있음)을 준다.
2. `target=law` 가 **일부라도 시행 중인 공포본**을 주되, 본문·시행일은 그 공포의 **마지막 시행일 기준**이다(단계적 시행).
3. 로컬 데이터만 오염됐다(테스트 시드 등).

## 3. 메트릭/실측 기반 원인 확인 (근거)

**API 안내서**(open.law.go.kr): `lawService.do?target=law` 는 **"현행법령(공포일) 본문 조회"**, `target=eflaw` 는 **"현행법령(시행일) 본문 조회"**. `eflaw` 목록에는 `nw`(1 연혁 · 2 시행예정 · 3 현행)와 `LID`(법령ID) 필터가 있다.

**실측(호출 10회):**

| 조회 | 주택법 001809 결과 |
|---|---|
| `lawSearch target=eflaw&LID=001809&nw=1,2,3` | 21902(MST 289171)가 **두 행** — 2026-09-08 `현행` · 2027-03-09 `시행예정`. 21901(타법개정) 2027-09-09 `시행예정` |
| `lawSearch target=law&query="주택법"` | 21902 · 시행일자 **20260908** |
| `lawService target=law&ID=001809` (원본 보관) | 21902 · 시행일자 **20270309** · `조문시행일자문자열 = 20260908:제21조제1항` |
| `lawService target=eflaw&ID=001809` (efYd 생략) | 21902 · 시행일자 **20260908** |
| `lawSearch target=eflaw&LID=001809&nw=3` | **1행** — MST 289171 · 20260908 |
| `lawSearch target=eflaw&LID=015136&nw=3` (제정, 현행 없음) | **`totalCnt=0`** (JSON) |
| `lawService target=eflaw&ID=015136` (제정) | **HTML "미신청된 목록/본문에 대한 접근입니다"** |

→ 21902 는 공포일(2026-09-08)에 제21조제1항만 시행되고 나머지는 2027-03-09 시행이다. `target=law` 는 이 공포본을 "현행"으로 고르되 **본문은 마지막 시행일 기준**으로 준다. 가설 2 확정. 가설 1 기각 — 최신 공포본이면 시행예정 법령 거의 전부가 걸려야 하는데 실제로는 32건뿐이다.

**로컬 DB(2026-11-01):** `status='시행중' AND effective_date > 오늘(서울)` **32행**.
- 32행 전부 같은 `(law_id, effective_date)` 의 SYNC 작업 이력이 있다 → 원래 **시행예정 행이었다**. 가설 3 기각.
- 29행은 부칙에 "공포한 날부터 시행" 단서. 3행은 부칙 첫 문장엔 단서가 없지만 목록상 시행일별 버전이 있다(전공의법 21267: 02-21 연혁 · 03-31 현행 · 12-31 시행예정).

**피해 경로:** `IngestService.store` 가 시행예정 행을 쓴 뒤 기준선을 upsert 한다. 기준선 시행일이 시행예정본과 같으면 **같은 PK** 라 시행예정 행이 `시행중` 으로 덮어써진다. 이후 SYNC 가드(저장 행이 시행중이면 건너뜀)가 복구를 막는다.

| 영향 | 범위 |
|---|---|
| 시행예정 버전이 사라짐 — 곧 시행 목록(D65)·알림(D74)·요약(D67)·발견(D70)에서 빠지고 북마크(D72)엔 `시행됨` | 32버전 |
| 같은 법령의 다른 시행예정본 diff·Layer B 현행 블록·근거 칩 "현행"이 미래본 기준 | 13법령 |

## 4. 해결 방안 비교

| 안 | 내용 | 판단 |
|---|---|---|
| A. `target=eflaw&ID=` (efYd 생략) | 호출 1회, 개정 법령엔 시행일 기준 현행을 준다 | ✗ 제정 법령에 HTML 권한 오류 페이지 — 진짜 권한 오류와 구분 불가 |
| B. `target=law` 유지 + 본문 시행일 > 오늘이면 거부 | 변경 최소 | ✗ 단계적 시행 법령은 기준선을 아예 못 얻는다 |
| **C. 목록 `eflaw&LID=&nw=3` → 본문 `eflaw&MST=&efYd=`** | 호출 2회. 0건 = 제정(JSON 으로 판정), 1건 = 그 시행일 본문. 본문 경로는 시행예정과 같다 | ✓ 채택(D76) |

두 번째 방어선: 커넥터가 현행 행 2건 이상·본문 시행일 불일치를 거부하고, `IngestService` 가 기준선 시행일 ≥ 시행예정본 시행일을, `SyncHandler` 가 기준선 시행일 > 오늘을 거부한다(영구 실패 → DEAD).

## 5. 최종 적용·검증

- `LawConnector.fetchCurrent` — 목록 `eflaw&LID=&nw=3` → 0건 `null` / 1건이면 본문 `eflaw&MST=&efYd=`(상태 `시행중`). 현행 행 복수·MST/시행일 누락·본문 시행일 불일치는 `LawApiException`. 계측 태그 `target=current`.
- `IngestService.store` — 기준선 시행일 ≥ 시행예정본 시행일이면 쓰기 전에 `IllegalArgumentException`.
- `SyncHandler` — 기준선 시행일 > 오늘(서울)이면 트랜잭션 롤백 + `IllegalArgumentException`(영구 실패 → DEAD).
- `LawEnvelope.hasLawBody` 제거 — `target=law` 의 `{"Law":...}` 봉투 판별용이었다.

**검증:**
- 단위 `LawConnectorCurrentTest`(로컬 HTTP 서버, 쿼터 0) 4건 — 현행 행 → MST+efYd 본문·`ID` 미사용 / 0건 → `null`·본문 미호출 / 복수 → 예외 / 시행일 불일치 → 예외. RED 확인 후 GREEN.
- 통합 `IngestServiceIntegrationTest.기준선_시행일_불변식`(같은 날 기준선 → 거부·무저장), `TaskHandlersIntegrationTest.sync_미래기준선_거부`(롤백·등록 없음). core 전체 683건 중 실패는 라이브 스모크 6건뿐 — 아래 별건.
- 로컬 DB: 오염 행 32건을 CSV 로 백업한 뒤 삭제 → `시행중`·시행일 > 오늘 **0건**. 해당 시행예정 버전은 다음 `run` 의 탐색이 새 버전으로 다시 SYNC 한다.

> **별건(같은 날 발견):** `lawSearch target=eflaw&efYd=..` 가 `nw` 없이 `totalCnt=0` 을 준다(`nw=2` 를 붙이면 875). 목록 기반 라이브 스모크가 모두 실패하는 원인이며 `listPending`·`searchPending` 이 영향을 받는다 — 빈 목록이 "완주"로 판정되면 사라짐 처리(PURGE)로 이어질 수 있어 고치기 전까지 로컬 `run` 을 돌리지 않는다. 이 문서 범위 밖이라 따로 다뤘다 — [[007-eflaw-list-needs-nw|007]] · D77.

**교훈:** 이름이 같은 "현행"도 **기준 시점이 다를 수 있다**(공포일 vs 시행일). 외부 API 의 의미는 이름이 아니라 **안내서와 실응답**으로 확인하고, 저장 키가 겹칠 수 있는 두 쓰기는 **불변식으로 막는다**.
