
## 2026-08-02 세션
- LawConnector 3종 파일 개괄 (RawLaw / LawEnvelope / LawConnector) — Quick
  - 더 알아보기: ①Java record ②LawEnvelope 방어적 파싱 5함정 ③listPending 페이징 루프 ④request() 재시도·예외 분기 ⑤flatten의 switch 패턴 매칭 ⑥SourceConnector 미구현 설계 결정(D42)
- LawConnector 3종 → 디자인 패턴 심층 분석 (Strategy/Adapter/Command/Registry/Factory) — Deep Dive
  - 핵심: SourceConnector = Strategy 계약이나 LawConnector는 의도적 미참여(D42). SourceAnalyzer.searchAll=fan-out(Composite), getByBillNo=Chain of Responsibility
  - 더 알아보기: ①List<T> 자동수집 주입 ②Adapter vs Strategy 구분 ③Command 패턴 requirements ④Envelope의 static 유틸이 패턴이 아닌 이유 ⑤LawConnector에 인터페이스를 붙인다면 ⑥재시도/서킷브레이커
