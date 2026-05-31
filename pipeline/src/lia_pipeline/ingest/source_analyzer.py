"""사용자 입력(링크/영상/텍스트) → 법안 식별(resolve).

핵심 원칙: LLM은 '분석가'가 아니라 '식별자(resolver)'다.
뉴스/영상에서 어떤 법안을 가리키는지만 추출하고, 실제 데이터는
반드시 신뢰 출처에서 다시 가져온다. (뉴스가 틀려도 원문이 기준)
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field

from rapidfuzz import fuzz

from ..connectors.base import SourceConnector
from ..models import RawBill


@dataclass
class ResolveResult:
    matched: RawBill | None = None
    candidates: list[RawBill] = field(default_factory=list)
    needs_confirmation: bool = False


class SourceAnalyzer:
    def __init__(self, connectors: list[SourceConnector]) -> None:
        self.connectors = connectors

    # --- 입력 → 텍스트 ---------------------------------------------------
    def to_text(self, source: str) -> str:
        if source.startswith("http"):
            return self._extract_url(source)
        return source

    def _extract_url(self, url: str) -> str:
        # 영상(youtube 등)은 자막/STT 경로로 분기. TODO.
        try:
            import trafilatura
            downloaded = trafilatura.fetch_url(url)
            return trafilatura.extract(downloaded) or ""
        except Exception:
            return ""

    # --- 텍스트 → 법안 엔티티 -------------------------------------------
    def extract_bill_hint(self, text: str) -> dict:
        """법안명/의안번호 후보 추출. PoC는 규칙 기반, 운영은 LLM 병행."""
        bill_no = None
        m = re.search(r"의안번호\s*([0-9]{6,})", text)
        if m:
            bill_no = m.group(1)
        # TODO: LLM으로 법안명/소관위/키워드 추출
        title_hint = text[:40]
        return {"bill_no": bill_no, "title_hint": title_hint}

    # --- 엔티티 → 표준 법안 해소 ----------------------------------------
    def resolve(self, source: str) -> ResolveResult:
        text = self.to_text(source)
        hint = self.extract_bill_hint(text)
        candidates: list[RawBill] = []
        for conn in self.connectors:
            candidates.extend(conn.search(hint["title_hint"], limit=10))

        if not candidates:
            return ResolveResult(needs_confirmation=True)

        scored = sorted(
            candidates,
            key=lambda b: fuzz.token_set_ratio(b.title, hint["title_hint"]),
            reverse=True,
        )
        best = scored[0]
        confident = fuzz.token_set_ratio(best.title, hint["title_hint"]) >= 85
        return ResolveResult(
            matched=best if confident else None,
            candidates=scored[:5],
            needs_confirmation=not confident,
        )
