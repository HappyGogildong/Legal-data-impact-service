"""표준 도메인 모델.

커넥터/출처마다 응답 형태가 다르지만, 파이프라인 밖으로는 이 모델만 나간다.
진행단계(Stage)와 시행예정일(effective_date)을 1급 시민으로 두는 것이
'현행법' 서비스와의 핵심 차이다 — 일반 시민에겐 "언제부터 바뀌나"가 가장 중요.
"""
from __future__ import annotations

from datetime import date
from enum import Enum

from pydantic import BaseModel, Field


class Stage(str, Enum):
    PROPOSED = "발의"
    COMMITTEE = "위원회심사"
    PLENARY = "본회의"
    TRANSFERRED = "정부이송"
    PROMULGATED = "공포"
    EFFECTIVE = "시행"


class ChangeType(str, Enum):
    NEW = "신설"
    AMEND = "개정"
    DELETE = "삭제"


class Article(BaseModel):
    """조문."""
    no: str                              # 예: "제12조"
    title: str | None = None
    text: str
    change_type: ChangeType | None = None
    diff_vs_current: str | None = None   # 현행 조문 대비 diff


class RawBill(BaseModel):
    """커넥터가 내보내는 출처 비종속 원자료. 정규화 전 단계."""
    source_type: str                     # "assembly" | "moleg" | ...
    source_id: str                       # 출처 내 식별자
    bill_no: str | None = None           # 의안번호
    title: str
    raw: dict = Field(default_factory=dict)  # 출처 원본 페이로드


class Bill(BaseModel):
    """정규화된 표준 법안 모델 (저장소·코어 공용)."""
    id: str
    bill_no: str | None = None
    title: str
    summary: str | None = None
    proposers: list[str] = Field(default_factory=list)
    propose_date: date | None = None
    committee: str | None = None
    stage: Stage = Stage.PROPOSED
    effective_date: date | None = None   # 시행예정일
    source_type: str = ""
    source_url: str | None = None
    full_text: str | None = None
    articles: list[Article] = Field(default_factory=list)
    baseline_law_id: str | None = None   # 현행법 diff 기준선


class Citation(BaseModel):
    """모든 분석 주장은 일차 출처(조문)로 역추적된다."""
    article_no: str
    quote: str
    verified: bool = False


class ImpactResult(BaseModel):
    """커맨드 후처리 산출물."""
    bill_id: str
    persona: str | None = None
    summary: str
    impacts: list[str] = Field(default_factory=list)
    actions: list[str] = Field(default_factory=list)
    citations: list[Citation] = Field(default_factory=list)
    confidence: float = 0.0
