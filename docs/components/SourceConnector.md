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

> ⚠️ **`RawBill`에는 본문(`fullText`)이 없다** — 목록 API가 메타데이터만 주기 때문(아래 §본문 획득). [[Normalizer]]가 요구하는 조문·부칙·신구조문대비표는 **별도 획득 경로**로 채워야 하며, 경로 확정 전까지 해당 파싱 동작은 실행 불가다.

## 본문(fullText) 획득 — **미해결 갭 (2026-07-21 실측)**

`Bill.fullText`·`articles[]`·부칙·신구조문대비표는 [[bill-attributes]]에서 **🔵B(의안 원문 파싱)** 계층으로 분류돼 있고 [[analysis-prompt-spec]] §1 **요소 4(법안 조문 본문)는 필수**지만, **어느 문서도 "본문을 어떻게 가져오는가"를 규정하지 않았다.** 실측 결과:

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

**영향 범위:** 본문 없이는 `LawDiff`·조문 인용 그라운딩·신구조문대비표(임베딩 벤치 시나리오 A 정답쌍)가 **모두 성립하지 않는다** → MVP 필수 관문.
**인터페이스 확장 예정:** `SourceConnector`에 `fetchFullText(sourceId)` 추가(경로 확정 후).

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
```java
public interface SourceConnector {
    String sourceType();
    List<RawBill> search(String query, int limit);
    RawBill fetch(String sourceId);
    default RawBill getByBillNo(String billNo) { ... }   // 기본: search 결과에서 정확 일치
}

class AssemblyBillsConnector implements SourceConnector  // 열린국회 — 구현 완료 ✅
// MolegNoticeConnector  — 법제처 입법예고(정부입법) → RawBill  (이슈 #11)
// LawConnector          — 국가법령정보(현행법)     → RawLaw   (이슈 #11)
```

## 구조 결정 의도 (왜 이렇게)
- **개방-폐쇄.** 출처 다양성을 인터페이스 뒤로 숨겨, 출처 추가가 코어·하류를 건드리지 않게 함(법제처가 MVP 내 실증, [[decision-log|D24]]).
- **출처 누수 차단.** 밖으로 출처 고유 필드를 흘리지 않고 `Raw*`로만 노출 → Normalizer가 출처를 몰라도 됨(수집↔해석 분리).
- **법안 vs 현행법 산출 분리(`RawBill`/`RawLaw`).** 둘은 하류 경로(Normalizer vs RAG Indexer)가 다르므로 타입으로 구분.
- 정규화를 **여기서 하지 않는다** — 매핑만. 표준 모델 변환은 [[Normalizer]] 책임(단일 책임).

## 의존 / 관련
- 의존: 각 출처 API 키/OC
- 다음 단계: [[Normalizer]](법안), [[RAGIndexer]](현행법)
- 상세 표: [[components-io-and-scope]] §1
