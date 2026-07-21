"""사용자 입력(링크/텍스트/식별자) → 법안 식별(resolve).

핵심 원칙 (설계: docs/components/SourceAnalyzer.md):
- LLM/규칙은 '분석가'가 아니라 '식별자(resolver)'다. 입력 *내용*을 사실로 받지 않고
  "어떤 법안인가"만 찾는다. 실제 데이터는 신뢰 출처 원문이 기준.
- **fail-closed**: 신뢰 출처에서 확인되지 않으면 분석하지 않는다.
- 해소 4상태로 결과를 구분한다(아래 ResolutionState).
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from enum import Enum

from ..connectors.base import SourceConnector
from ..models import RawBill


class ResolutionState(str, Enum):
    RESOLVED = "RESOLVED"            # 출처에서 1건 확인 → 분석 진행
    AMBIGUOUS = "AMBIGUOUS"          # 후보 여럿 → 사용자 확인
    NOT_FOUND_YET = "NOT_FOUND_YET"  # 미등록(아직 발의 전/지연) → 거부+안내
    UNVERIFIED = "UNVERIFIED"        # 허위 의심 → 거부+(유사 법안 대조)


@dataclass
class ResolutionResult:
    state: ResolutionState
    resolved: RawBill | None = None
    candidates: list[RawBill] = field(default_factory=list)  # AMBIGUOUS 후보
    similar: list[RawBill] = field(default_factory=list)     # UNVERIFIED 대조용
    message: str | None = None


# 법안 식별자처럼 보이는가 (미등록 vs 허위 구분에 사용)
_BILLISH = re.compile(r"(법률안|개정안|제정안|법안|개정법률안|일부개정|전부개정|「.+?」)")
_BILL_NO = re.compile(r"(?:의안\s*번호\s*)?(\d{6,})")


def _ratio(a: str, b: str) -> float:
    """토큰 기반 유사도(0~100). rapidfuzz 있으면 사용, 없으면 difflib 폴백."""
    try:
        from rapidfuzz import fuzz
        return float(fuzz.token_set_ratio(a, b))
    except ImportError:
        import difflib
        return difflib.SequenceMatcher(None, a, b).ratio() * 100.0


class SourceAnalyzer:
    def __init__(
        self,
        connectors: list[SourceConnector],
        *,
        semantic_search=None,   # Callable[[str], list[RawBill]] | None — 법안 의미검색(Vector Index). 미구현 시 None
        confident: float = 88.0,
        ambiguous_min: float = 60.0,
        strict: bool = False,   # True면 커넥터 오류를 전파, False면 스킵(오프라인/무키 견고)
    ) -> None:
        self.connectors = connectors
        self.semantic_search = semantic_search
        self.confident = confident
        self.ambiguous_min = ambiguous_min
        self.strict = strict

    # --- 입력 → 텍스트 -------------------------------------------------
    def to_text(self, source: str) -> str:
        if source.startswith("http"):
            return self._extract_url(source)
        return source

    def _extract_url(self, url: str) -> str:
        try:
            import trafilatura
            downloaded = trafilatura.fetch_url(url)
            return trafilatura.extract(downloaded) or ""
        except Exception:
            return ""

    # --- 텍스트 → 엔티티 -----------------------------------------------
    def extract_entities(self, text: str) -> dict:
        """법안명/의안번호 후보 추출. 규칙 기반(운영은 LLM 병행 — TODO)."""
        bill_no = None
        m = re.search(r"의안\s*번호\s*(\d{6,})", text)
        if m:
            bill_no = m.group(1)
        title_hint = text.strip().splitlines()[0][:60] if text.strip() else ""
        return {"bill_no": bill_no, "title_hint": title_hint, "billish": bool(_BILLISH.search(text))}

    # --- 엔티티 → 해소 -------------------------------------------------
    def resolve(self, source: str, input_type: str = "auto") -> ResolutionResult:
        text = source if input_type in ("text", "title", "billNo") else self.to_text(source)
        ent = self.extract_entities(text)

        # 1) 의안번호 정확 조회
        if ent["bill_no"]:
            raw = self._get_by_bill_no(ent["bill_no"])
            if raw:
                return ResolutionResult(ResolutionState.RESOLVED, resolved=raw)
            return ResolutionResult(
                ResolutionState.NOT_FOUND_YET,
                message=f"의안번호 {ent['bill_no']}를 신뢰 출처에서 확인하지 못했습니다(아직 발의 전이거나 미등록일 수 있음).",
            )

        # 2) 법안명/키워드 정확·퍼지 매칭
        query = ent["title_hint"]
        candidates = self._search_all(query) if query else []
        if candidates:
            scored = sorted(candidates, key=lambda b: _ratio(b.title, query), reverse=True)
            strong = [b for b in scored if _ratio(b.title, query) >= self.confident]
            if len(strong) == 1:
                return ResolutionResult(ResolutionState.RESOLVED, resolved=strong[0])
            if len(strong) >= 2:   # 동명/유사 법안 다수 → 단정 금지
                return ResolutionResult(
                    ResolutionState.AMBIGUOUS, candidates=strong[:5],
                    message="동일·유사 제목 법안이 여럿입니다. 어느 법안을 말씀하시나요?",
                )
            decent = [b for b in scored if _ratio(b.title, query) >= self.ambiguous_min][:5]
            if decent:
                return ResolutionResult(
                    ResolutionState.AMBIGUOUS, candidates=decent,
                    message="여러 법안이 후보로 잡혔습니다. 어느 법안을 말씀하시나요?",
                )

        # 3) 의미검색(법안 네임스페이스) — 미구현 시 None
        if self.semantic_search:
            sims = list(self.semantic_search(text))
            if sims:
                return ResolutionResult(
                    ResolutionState.AMBIGUOUS, candidates=sims[:5],
                    message="유사 법안을 찾았습니다. 의도하신 법안인지 확인해 주세요.",
                )

        # 4) 미등록 vs 허위 구분 (fail-closed)
        if ent["billish"]:
            return ResolutionResult(
                ResolutionState.NOT_FOUND_YET,
                message="해당 법안을 신뢰 출처에서 확인하지 못했습니다(아직 발의 전이거나 미등록일 수 있음).",
            )
        return ResolutionResult(
            ResolutionState.UNVERIFIED,
            message="확인되지 않은 정보입니다. 실재하는 법안과 매칭되지 않습니다.",
        )

    # --- 내부 ----------------------------------------------------------
    def _search_all(self, query: str) -> list[RawBill]:
        out: list[RawBill] = []
        for conn in self.connectors:
            try:
                out.extend(conn.search(query, limit=10))
            except Exception:
                if self.strict:
                    raise
        return out

    def _get_by_bill_no(self, bill_no: str) -> RawBill | None:
        for conn in self.connectors:
            try:
                raw = conn.get_by_bill_no(bill_no)
            except Exception:
                if self.strict:
                    raise
                raw = None
            if raw:
                return raw
        return None
