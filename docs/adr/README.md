# ADR 색인 (Architecture Decision Records)

아키텍처 **결정**을 기록한다. 버전별 설계 스냅샷(`../architecture/`)이 "현재 구조가 무엇인가"를 보여준다면, ADR은 "왜 그렇게 결정했나 / 어떤 대안을 버렸나"를 남긴다.

작성 규약:
- 파일명: `ADR-NNN-짧은설명.md` (NNN은 0부터 시작하는 일련번호, 3자리)
- 상태: `Proposed → Accepted → Deprecated | Superseded`. 바뀌면 상단 frontmatter와 본문 Status를 갱신.
- 결정이 뒤집히면 ADR을 지우지 않고 새 ADR을 추가한 뒤 옛 ADR을 `Superseded by ADR-NNN`으로 표기.

> 전체 결정의 빠른 인덱스는 [decision-log.md](decision-log.md)(Living) 참고 — 개별 ADR로 승격되기 전 결정도 여기서 추적한다.

## 목록

| ADR | 제목 | 상태 | 날짜 |
|---|---|---|---|
| [ADR-001](ADR-001-knowledge-store-sizing.md) | 지식 저장소 구성 — 분리형 vs 통합형(Postgres+pgvector) | Proposed | 2026-06-22 |
| [decision-log](decision-log.md) | 결정 로그 (전체 결정 요약 인덱스) | Living | 2026-06-25 |
