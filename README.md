# 입법 영향 분석기 (LIA · Legislative Impact Analyzer)

입법 예정 법안이 **나에게 어떤 변화를 주는지, 무엇을 해야 하는지**를
일반 시민의 언어로 알려주는 서비스. 확장 가능한 폴리글랏 구조.

> 설계 상세는 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) 참고.

## 구성 (3-런타임)

| 디렉터리 | 언어 | 책임 |
|---|---|---|
| [`pipeline/`](pipeline) | Python | 신뢰 출처 수집·소스 분석·LLM 영향 추론 |
| [`core/`](core) | Java Spring | 도메인 모델·use-case별 커맨드 오케스트레이션·REST API |
| [`mcp/`](mcp) | TypeScript | 웹 프론트엔드 (일반 시민 주 경로) + 선택적 MCP 어댑터 |

데이터 흐름: **출처 → 정규화 → 저장 → 커맨드 후처리 → 사용자 표면(웹앱)**

```
신뢰 출처(국회·법제처 OpenAPI) ┐
사용자 입력(링크·영상)        ┴→ pipeline → 저장소 → core(커맨드) → 웹앱/(선택)MCP → 사용자
```

## 빠른 시작 (PoC)

```bash
# 1) 파이프라인 (Python)
cd pipeline && pip install -e . && python -m lia_pipeline.demo

# 2) 코어 (Java Spring)
cd core && ./gradlew bootRun

# 3) MCP 서버 (TypeScript)
cd mcp && npm install && npm run dev
```

또는 한 번에:

```bash
docker compose up
```

## 상태

PoC 스캐폴드. 각 모듈의 핵심 인터페이스와 한 개의 수직 슬라이스
(법안 1건 수집 → 영향 요약 → 웹 화면 표시, REST 경유)를 채우는 것이 다음 단계.
`TODO` 주석이 구현 지점을 표시한다.

## 면책

법률 자문이 아닌 참고용 정보 제공 서비스다. 모든 결과는 의안 원문·조문 등
일차 출처로 역추적 가능하도록 설계되어 있다.
