---
title: SourceConnector — 컴포넌트 설계
status: Draft
date: 2026-06-30
tags: [component, pipeline, connector]
related: ["components/component-specs.md", "architecture/v0.5-bill-discovery.md"]
---

# SourceConnector (Spring, 수집)

> **런타임 변경(D35):** 구현 런타임이 Python → **Spring(Boot 4.0 + Spring AI 2.0)** 으로 통합됨([[v0.6-spring-consolidation|v0.6]] · [[spring-migration|버전 변경점]]). 본 문서의 역할·입출력·동작·결정 의도는 그대로 유효하며, Python 인터페이스 초안은 **포팅 사양**으로 유지된다.


> 출처별 API 호출을 흡수해 **표준 Raw 객체**만 하류로 내보내는 어댑터. 관련: [[component-specs]] §4 #1 · [[v0.5-bill-discovery]] §3.1

## 역할
출처마다 다른 인증·페이징·필드명을 **커넥터 안에 가두고**, 밖으로는 `RawBill`(법안) 또는 `RawLaw`(현행법)만 노출한다. 새 출처 = 새 구현체 1개(하류 무수정).

## 입력 / 출력
| | 타입 | 설명 |
|---|---|---|
| 입력 | `BillQuery{ since?, billNo?, keyword?, page? }` / `LawQuery{ lawId|lawName }` | 출처 조회 조건 |
| 출력 | `Iterable[RawBill]` 또는 `Iterable[RawLaw]` | 출처 원형 필드 보존(아직 정규화 전) |

### `RawBill` 필드 정의 (2026-07-21 명문화)
지금까지 문서 전반에서 `RawBill`을 이름으로만 참조하고 **필드를 정의한 곳이 없었다.** 현행 구현 기준:

```java
record RawBill(String sourceType, String sourceId, String billNo, String title,
               Map<String,Object> raw)   // 출처 원본 페이로드(정규화 전)
```

> ⚠️ **`RawBill`에는 본문(`fullText`)이 없다** — 목록 API가 메타데이터만 주기 때문. **단 이는 `assembly`(의원발의) 한정이며 MVP 경로가 아니다**(D42). MVP는 아래 `RawLaw`를 쓴다.

---

## ✅ MVP 본문 경로 — `RawLaw` (국가법령정보 `eflaw`) · **2026-08-01 실측 확정 (D42)**

**MVP 분석 대상은 의안이 아니라 "공포됐으나 아직 시행 전인 법령"이다.** 통과 여부가 확정돼 불확실성이 0이고, 국가법령정보가 **전문·개정문·제개정이유·부칙을 모두 제공**한다. 별도 커넥터·HWP 파서가 필요 없다.

```java
record RawLaw(String lawId, String mst, String title, String status,   // "시행예정"
              LocalDate effectiveDate, LocalDate promulgateDate, String promulgateNo,
              Map<String,Object> raw)   // 법령 > {기본정보, 조문, 부칙, 개정문, 제개정이유}
```

### 획득 2단계

```
# 1) 목록 — 시행예정만 (오늘 이후 시행일 범위)
GET {law.base}/DRF/lawSearch.do?OC=..&target=eflaw&type=JSON
      &efYd=20260802~20271231&sort=efasc&display=200
  → totalCnt 899 · 각 row: 법령ID·법령일련번호(MST)·법령명한글·시행일자·공포일자·공포번호
                          ·제개정구분명·법종구분·소관부처명·현행연혁코드("시행예정")

# 2) 본문
GET {law.base}/DRF/lawService.do?OC=..&target=eflaw&MST=283191&type=JSON&efYd=20260804
  → 299KB · 법령 > {기본정보, 조문, 부칙, 개정문, 제개정이유}

# 3) diff 기준선(현행본) — ★ 법령ID(ID)로 조회. MST는 버전마다 달라 연결키로 못 쓴다
GET {law.base}/DRF/lawService.do?OC=..&target=law&ID=001809&type=JSON
```

### 응답 계약 (주택법 MST=283191, 시행 2026-08-04 실측)

| 블록 | 내용 | 매핑 |
|---|---|---|
| `기본정보` | 법령ID·법령명_한글·제개정구분·법종구분·소관부처·공포일자·공포번호·시행일자 | `Law` 헤더 |
| `조문.조문단위[]` | **137개** — `조문번호·조문제목·조문내용·조문제개정유형·조문시행일자·`**`조문변경여부`**`·조문이동이전/이후·조문키·조문여부` | `Article[]` |
| `부칙.부칙단위[]` | 42개(이력 전체) — `부칙공포일자·`**`부칙공포번호`**`·부칙내용` | `Addendum[]` |
| `개정문` | 2,002자 — 자구 단위 개정 지시문 | `Law.amendText` |
| `제개정이유` | 370자 — "◇ 개정이유 및 주요내용" | `Law.amendReason` |

**실측으로 확인된 규칙 4가지:**

1. **`조문변경여부='Y'`가 이번 개정으로 바뀐 조문을 지목한다** — 137개 중 6개(제18·28·46·49·104·106조). `개정문` 정규식 파싱은 오탐(타법 인용 제15·27조)·누락(제104·106조)을 냈다. **플래그가 정답.**
2. **부칙은 `부칙공포번호 == 기본정보.공포번호`로 필터** — 42개 중 이번 개정분 1개. 여기서 `effectiveRule`이 그대로 나온다: *"공포 후 6개월이 경과한 날부터 시행한다. 다만, 제57조제2항제7호의 개정규정은 공포한 날부터 시행한다"* → `enforcementType="단계적"`.
3. **`조문내용`만 읽으면 본문이 빈다** — 제2조(정의)는 `조문내용`이 제목 줄뿐이고 실제 정의는 `항 → 호 → 목` 중첩에 있다. **재귀 병합 필수.**
4. **`기본정보.소관부처` 등 일부 필드가 중첩 객체**다(문자열 아님). 평탄화 필요.
5. **`display=1` 이면 `law` 가 배열이 아니라 단일 객체**로 온다(2026-08-02 구현 중 발견). 리스트로 감싸지 않으면 단건 조회가 조용히 0건이 된다.
6. **인증 실패도 HTTP 200** 이다 — `{"result":"사용자 정보 검증에 실패하였습니다.","msg":...}` 가 본문으로 온다. 상태코드로는 판별 불가하므로 **봉투 검사가 유일한 방어선**이다.

> 재현: `python tools/probe_eflaw.py [MST] [efYd]` — 목록 필드·본문 구조·조문 플래그·부칙 필터·현행본 연결을 한 번에 실측한다.

### 신구조문대비표는 불필요

`Article.oldNewTable`(HWP 첨부 파싱)은 **폐기**한다. 같은 `lawId`의 시행중본(`target=law&ID=`)과 시행예정본(`target=eflaw&MST=`)이 **동일 스키마로 조문 전문을 주므로** 조문 단위 대조를 직접 만들 수 있고, `개정문`이 권위 있는 자구 변경 근거가 된다. 임베딩 벤치 시나리오 A의 정답쌍도 이 경로로 생성한다.

---

## 법안 본문 — `assembly` 잔여 갭 *(post-MVP 강등, D42)*

> **MVP 차단 요인이 아니다.** 의원발의는 통과율 ~20%로 "적용 예정" 성격이 약해 **참고용 소스**로만 유지한다. 통과율 20%짜리 소스를 위해 가장 비싼 파서(HWP)를 만드는 것은 우선순위 역전이다. 아래는 2026-07-21 실측 기록의 보존이다.

의안 경로에서는 `fullText`·`articles[]`·부칙·신구조문대비표가 **원문(HWP/PDF) 파싱**을 거쳐야 하는 계층이었는데, **어느 문서도 "본문을 어떻게 가져오는가"를 규정하지 않았다.** 실측 결과:

| 경로 | 결과 |
|---|---|
| 목록 API `nzmimeepazxkubdpn` (24필드) | ❌ 본문·제안이유·조문 **전무** — 메타데이터만 |
| 상세 페이지 `DETAIL_LINK` | ❌ JS 렌더 — 정적 HTML에 "제안이유/주요내용/부칙" 문자열 없음. **HWP 첨부만 존재** |
| 대체 API 4종 — `BILLINFODETAIL`(의안 상세)·`BILLINFOPPSR`(제안자)·`TVBPMBILL11`(통합)·`BILLRCP`(접수) | ❌ 모두 응답하나 **본문 없음**. 값 길이 200자 초과 필드가 하나도 없음(심사 단계 일자·발의자 명단뿐) |

> ⚠️ **열린국회 API 함정 (2026-07-31, Java 포팅 중 발견):** `Type=json` 으로 요청해도 서버가 **`Content-Type: text/html`** 로 응답한다(본문은 정상 JSON). Spring `RestClient`가 메시지 컨버터로 바인딩하면 `UnknownContentTypeException` 이 난다 → **`String`으로 받아 직접 파싱**해야 한다(`AssemblyBillsConnector.parseJson`). Python `httpx`는 `.json()`이 강제 파싱해 이 문제가 드러나지 않았다.

> 재현: `python tools/probe_sources.py [검색어]` — 자격증명·목록 필드·본문 서비스 탐색을 한 번에 실측한다.
> **판정 기준 주의:** 필드명(`*_CN`, `*CONTENT`)으로 본문을 판별하면 오탐한다 — `PPSR_CN`은 "김기표의원 등 11인"(발의자 내용)이다. **값 길이(>200자)** 로 판정할 것.

### ✅ 현행법(국가법령정보)은 본문 제공 확인 — 2026-07-31 실측
반면 **국가법령정보(`law` 출처)는 조문 본문을 완전히 제공한다.** 즉 갭은 *법안(assembly)* 에만 존재한다.

```
GET {law.base}/DRF/lawSearch.do?OC=..&target=law&type=JSON&query=주택임대차보호법  → 법령일련번호(MST)
GET {law.base}/DRF/lawService.do?OC=..&target=law&MST=276291&type=JSON            → 본문 67KB
```
응답 구조: `법령 > {기본정보, 조문, 부칙, 개정문, 제개정이유}`
- **`조문.조문단위[]` 42개** — 필드 `조문번호·조문제목·조문내용·조문시행일자·조문변경여부·조문이동이전/이후·조문키`
- 예: `조문번호=1, 제목=목적, 조문내용="제1조(목적) 이 법은 주거용 건물의 임대차(賃貸借)에 관하여…"(81자)`
- **부칙도 별도 제공** ✓
→ `RawLaw` → 조문 파싱·RAG 적재(`law` 네임스페이스)·**diff 기준선**이 이 경로로 성립한다. `조문변경여부`·`조문이동이전/이후` 필드는 개정 추적에 직접 쓸 수 있다.

**법안 본문 후보 (택1 필요 — 미결정):**
1. 열린국회 **"의안 제안이유·주요내용" 계열 서비스** — 존재가 시사되나 서비스 ID 미확인(콘솔 로그인 확인 필요)
2. 상세 페이지 **HWP 첨부 다운로드 + hwplib 파싱** — JVM 네이티브, 품질 최상 / 구현 부담 큼
3. 상세 페이지 **JS 렌더 후 추출** — 비공식·취약(권장하지 않음)

**영향 범위(개정 — D42):** ~~MVP 필수 관문~~ **아니다.** `LawDiff`·조문 인용 그라운딩·벤치 정답쌍은 모두 위 `eflaw` 경로로 성립한다. 이 갭은 `assembly`를 *분석 대상*으로 승격할 때만 다시 열린다.
**인터페이스:** `SourceConnector.fetchFullText(sourceId)`는 `law` 구현체에서 먼저 제공한다(`assembly`는 미구현 스텁).

## 파라미터 (설정 — `application.yml` + `.env`)
비밀값은 **레포 루트 `.env` 하나**가 단일 소스(D39). `core/src/main/resources/application.yml`이 `${ENV_VAR}`로 참조하고, `LiaSourceProperties`(`@ConfigurationProperties`)가 타입 바인딩한다. 주입 경로: 로컬·테스트는 Gradle이 `.env`를 환경변수로 주입, 컨테이너는 compose `env_file: .env`.

| 파라미터 | application.yml 키 | 예 | 설명 |
|---|---|---|---|
| `api_key`/`oc` | `lia.sources.assembly.api-key` | `${ASSEMBLY_API_KEY:}` | 열린국회=ServiceKey, 법제처·국가법령=OC(회원 이메일 아이디) |
| `service` | `lia.sources.assembly.service` | `nzmimeepazxkubdpn` | 의원발의 법률안 목록 서비스 ID |
| `age` | `lia.sources.assembly.age` | `"22"` | **필수 파라미터** — 없으면 ERROR-300 |
| `base` | `lia.sources.assembly.base` | `open.assembly.go.kr/portal/openapi` | 엔드포인트 |
| `page-size` | `lia.sources.assembly.page-size` | 100 | 페이지당 건수(페이징 누적) |
| `timeout`, `max-retries` | `lia.sources.assembly.*` | 20s, 3 | 5xx/네트워크 지수백오프 |

> 커넥터 자체는 설정에 **비결합** — 평범한 인자를 받고 `PipelineConfig`가 프로퍼티를 주입해 조립한다(교체·테스트 용이).

## 동작
1. 쿼리 → 출처 요청 빌드(인증·페이징 파라미터 부착)
2. 페이지 순회하며 응답 → `RawBill`/`RawLaw`로 매핑
3. 레이트리밋 준수, 5xx→지수백오프 재시도, 4xx(키만료 등)→로그+스킵
4. **법안 커넥터는 Normalizer로, `LawConnector`는 RAG Indexer/Bill Store(기준선)로** 흐름

## 인터페이스 (Java, `com.lia.core.pipeline.connector`)

> **2026-08-02 정리:** `SourceConnector` 인터페이스와 `RawBill`·`AssemblyBillsConnector`·`AssemblyEnvelope`는 **코드에서 삭제**했다(D42로 의안이 분석 대상에서 빠짐). 계약은 본 문서에 남아 있으므로 의안 커넥터를 되살릴 때 여기서 복원한다(git 이력에도 보존).

```java
// ★ MVP 수집 경로 — 구현 완료 ✅ (이슈 #11)
public class LawConnector {
    List<RawLaw> listPending(LocalDate from, LocalDate to, int limit);   // target=eflaw + efYd
    List<RawLaw> searchPending(String query, LocalDate from, LocalDate to, int limit);
    RawLaw fetchPending(String mst, LocalDate effectiveDate);            // target=eflaw + MST
    RawLaw fetchCurrent(String lawId);                                   // target=law + ID
}

// 봉투 파싱 순수 함수 — 단위 테스트 대상
public final class LawEnvelope {
    checkError · extractRows · totalCount · lawRoot · basicInfo
    articles · changedArticles · addendaOf · text(재귀 평탄화) · date · str
}
```

**해소(resolve)와의 경계 — `LawLookup` 포트.** `SourceAnalyzer`는 커넥터를 직접 부르지 않는다. 아키텍처 v0.8 §3.2에서 해소는 **Law Store·Vector Index(오프라인 적재분)** 를 읽기 때문이다. 현재는 `PipelineConfig`가 `LawConnector`를 어댑터로 꽂아 두고, 저장소가 생기면 구현만 교체한다(`SourceAnalyzer` 무수정).

```java
public interface LawLookup {                       // com.lia.core.pipeline.resolve
    List<RawLaw> searchByName(String query, int limit);
}
```

### 의안 커넥터 계약 *(post-MVP — 되살릴 때 참고)*

```java
public interface SourceConnector {
    String sourceType();
    List<RawBill> search(String query, int limit);
    RawBill fetch(String sourceId);
    default RawBill getByBillNo(String billNo) { ... }
}
record RawBill(String sourceType, String sourceId, String billNo, String title, Map<String,Object> raw)
```
- `AssemblyBillsConnector` — 열린국회. `AGE` 필수, `Type=json` 이어도 `text/html` 응답(String 수신 후 Jackson 3 직접 파싱)
- `MolegNoticeConnector` — 법제처 입법예고. `.xml` 확장자 필수, `lmPpCts`에 개정이유·주요내용

## 구조 결정 의도 (왜 이렇게)
- **개방-폐쇄.** 출처 다양성을 인터페이스 뒤로 숨겨, 출처 추가가 코어·하류를 건드리지 않게 함(법제처가 MVP 내 실증, [[decision-log|D24]]).
- **출처 누수 차단.** 밖으로 출처 고유 필드를 흘리지 않고 `Raw*`로만 노출 → Normalizer가 출처를 몰라도 됨(수집↔해석 분리).
- **법안 vs 현행법 산출 분리(`RawBill`/`RawLaw`).** 둘은 하류 경로(Normalizer vs RAG Indexer)가 다르므로 타입으로 구분.
- 정규화를 **여기서 하지 않는다** — 매핑만. 표준 모델 변환은 [[Normalizer]] 책임(단일 책임).

## 의존 / 관련
- 의존: 각 출처 API 키/OC
- 다음 단계: [[Normalizer]](법안), [[RAGIndexer]](현행법)
- 상세 표: [[components-io-and-scope]] §1
