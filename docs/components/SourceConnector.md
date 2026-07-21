---
title: SourceConnector — 컴포넌트 설계
status: Draft
date: 2026-06-30
tags: [component, pipeline, connector]
related: ["components/component-specs.md", "architecture/v0.5-bill-discovery.md"]
---

# SourceConnector (Python, 수집)

> **런타임 변경(D35):** 구현 런타임이 Python → **Spring(Boot 4.0 + Spring AI 2.0)** 으로 통합됨([[v0.6-spring-consolidation|v0.6]] · [[spring-migration|버전 변경점]]). 본 문서의 역할·입출력·동작·결정 의도는 그대로 유효하며, Python 인터페이스 초안은 **포팅 사양**으로 유지된다.


> 출처별 API 호출을 흡수해 **표준 Raw 객체**만 하류로 내보내는 어댑터. 관련: [[component-specs]] §4 #1 · [[v0.5-bill-discovery]] §3.1

## 역할
출처마다 다른 인증·페이징·필드명을 **커넥터 안에 가두고**, 밖으로는 `RawBill`(법안) 또는 `RawLaw`(현행법)만 노출한다. 새 출처 = 새 구현체 1개(하류 무수정).

## 입력 / 출력
| | 타입 | 설명 |
|---|---|---|
| 입력 | `BillQuery{ since?, billNo?, keyword?, page? }` / `LawQuery{ lawId|lawName }` | 출처 조회 조건 |
| 출력 | `Iterable[RawBill]` 또는 `Iterable[RawLaw]` | 출처 원형 필드 보존(아직 정규화 전) |

## 파라미터 (설정 — `config.yaml`에서 주입)
파라미터는 코드/환경변수에 흩지 않고 **`config.yaml`(gitignore 대상)** 한 곳에서 관리한다. `pipeline/src/lia_pipeline/config.py`의 팩토리(`build_assembly_connector`)가 `Settings`를 읽어 커넥터를 조립한다. 값에 `${ENV_VAR}` 보간 지원(키 직접 입력도 가능). 커밋용 템플릿은 `config.example.yaml`.

| 파라미터 | config.yaml 키 | 예 | 설명 |
|---|---|---|---|
| `api_key`/`oc` | `sources.assembly.api_key` | `${ASSEMBLY_API_KEY}` 또는 직접값 | 열린국회=ServiceKey, 국가법령/국민참여=OC |
| `service` | `sources.assembly.service` | `nzmimeepazxkubdpn` | ⚠️ 서비스 ID — 콘솔 확인·교체 |
| `base` | `sources.assembly.base` | `open.assembly.go.kr/portal/openapi` | 엔드포인트 |
| `page_size` | `sources.assembly.page_size` | 100 | 페이지당 건수(페이징 누적) |
| `timeout`, `max_retries` | `sources.assembly.*` | 20s, 3 | 5xx/네트워크 지수백오프 |

> 커넥터 자체는 설정에 **비결합** — 평범한 인자를 받고 `config.py` 팩토리가 조립한다(교체·테스트 용이).

## 동작
1. 쿼리 → 출처 요청 빌드(인증·페이징 파라미터 부착)
2. 페이지 순회하며 응답 → `RawBill`/`RawLaw`로 매핑
3. 레이트리밋 준수, 5xx→지수백오프 재시도, 4xx(키만료 등)→로그+스킵
4. **법안 커넥터는 Normalizer로, `LawConnector`는 RAG Indexer/Bill Store(기준선)로** 흐름

## 인터페이스 (Python 초안)
```python
class SourceConnector(ABC):
    @abstractmethod
    def fetch(self, query: Query) -> Iterator[Raw]: ...

class AssemblyConnector(SourceConnector):  # 열린국회 — 의원발의 법안 → RawBill
class MolegConnector(SourceConnector):     # 법제처 입법예고 — 정부입법 → RawBill
class LawConnector(SourceConnector):       # 국가법령정보 — 현행법 → RawLaw
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
