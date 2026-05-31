"""열린국회정보 OpenAPI 커넥터 — 발의 법률안.

엔드포인트: https://open.assembly.go.kr/portal/openapi/...
인증키: ASSEMBLY_API_KEY (open.assembly.go.kr 발급)
"""
from __future__ import annotations

import os
from collections.abc import Iterable

import httpx

from ..models import RawBill
from .base import SourceConnector

BASE = "https://open.assembly.go.kr/portal/openapi"


class AssemblyBillsConnector(SourceConnector):
    source_type = "assembly"

    def __init__(self, api_key: str | None = None) -> None:
        self.api_key = api_key or os.environ.get("ASSEMBLY_API_KEY", "")

    def search(self, query: str, *, limit: int = 20) -> Iterable[RawBill]:
        # TODO: 실제 의안목록 서비스명/파라미터로 교체. (BILLNAME, AGE 등)
        params = {
            "KEY": self.api_key,
            "Type": "json",
            "pSize": limit,
            "BILL_NAME": query,
        }
        resp = httpx.get(f"{BASE}/nzmimeepazxkubdpn", params=params, timeout=20)
        resp.raise_for_status()
        rows = _extract_rows(resp.json())
        for row in rows:
            yield RawBill(
                source_type=self.source_type,
                source_id=row.get("BILL_ID", ""),
                bill_no=row.get("BILL_NO"),
                title=row.get("BILL_NAME", ""),
                raw=row,
            )

    def fetch(self, source_id: str) -> RawBill:
        # TODO: 의안 상세/본문 조회 엔드포인트 연결
        raise NotImplementedError("의안 상세 조회 미구현")


def _extract_rows(payload: dict) -> list[dict]:
    """열린국회정보 특유의 [head, row] 중첩 구조에서 row만 뽑는다."""
    for value in payload.values():
        if isinstance(value, list):
            for item in value:
                if isinstance(item, dict) and "row" in item:
                    return item["row"]
    return []
