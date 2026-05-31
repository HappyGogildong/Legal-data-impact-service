"""SourceConnector — 모든 출처 어댑터의 계약.

인증·페이징·필드명은 구현체 안에 가두고, 밖으로는 RawBill만 내보낸다.
"""
from __future__ import annotations

from abc import ABC, abstractmethod
from collections.abc import Iterable

from ..models import RawBill


class SourceConnector(ABC):
    #: 출처 식별자 (RawBill.source_type 에 기록)
    source_type: str

    @abstractmethod
    def search(self, query: str, *, limit: int = 20) -> Iterable[RawBill]:
        """키워드/조건으로 법안 목록 조회."""

    @abstractmethod
    def fetch(self, source_id: str) -> RawBill:
        """단건 상세(원문 포함) 조회."""
