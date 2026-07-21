"""SourceConnector — 모든 출처 어댑터의 계약.

인증·페이징·필드명은 구현체 안에 가두고, 밖으로는 RawBill만 내보낸다.
새 출처 = SourceConnector 구현체 한 개 추가(하류 무수정). 설계: docs/components/SourceConnector.md
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

    def get_by_bill_no(self, bill_no: str) -> RawBill | None:
        """의안번호 정확 조회.

        기본 구현은 ``search`` 결과에서 ``bill_no`` 정확 일치를 찾는다.
        출처가 의안번호 전용 파라미터를 지원하면 구현체에서 오버라이드한다.
        """
        for raw in self.search(str(bill_no), limit=20):
            if raw.bill_no and str(raw.bill_no) == str(bill_no):
                return raw
        return None
