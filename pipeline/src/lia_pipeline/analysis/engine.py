"""RAG + LLM 영향 추론 + 인용 검증.

코어(Java)의 커맨드가 호출하는 해석 엔진. 조문 원문과 현행법 diff를
컨텍스트로 넣고, 모든 주장에 근거 조문 ID를 붙인 뒤 교차검증한다(환각 차단).
"""
from __future__ import annotations

from ..models import Bill, Citation, ImpactResult


class AnalysisEngine:
    def __init__(self, llm=None) -> None:
        self.llm = llm  # TODO: OpenAI/사내 LLM 클라이언트 주입

    def summarize(self, bill: Bill, persona: str | None = None) -> ImpactResult:
        """평이한 요약 + (페르소나가 있으면) 개인화 영향."""
        # TODO: RAG 컨텍스트 구성 → LLM 호출 → 구조화 파싱
        context = self._build_context(bill)
        raw = self._call_llm(context, persona)            # placeholder
        citations = self._verify_citations(bill, raw.get("citations", []))
        return ImpactResult(
            bill_id=bill.id,
            persona=persona,
            summary=raw.get("summary", f"[PoC] '{bill.title}' 요약 미생성"),
            impacts=raw.get("impacts", []),
            actions=raw.get("actions", []),
            citations=citations,
            confidence=raw.get("confidence", 0.0),
        )

    def _build_context(self, bill: Bill) -> str:
        return "\n".join(a.text for a in bill.articles) or (bill.full_text or "")

    def _call_llm(self, context: str, persona: str | None) -> dict:
        # TODO: 실제 LLM 호출. PoC는 빈 구조 반환.
        return {}

    def _verify_citations(self, bill: Bill, claims: list[dict]) -> list[Citation]:
        """LLM이 인용한 조문이 실제 원문에 존재하는지 교차검증."""
        article_nos = {a.no for a in bill.articles}
        return [
            Citation(
                article_no=c["article_no"],
                quote=c.get("quote", ""),
                verified=c["article_no"] in article_nos,
            )
            for c in claims
        ]
