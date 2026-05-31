"""법제처 정부입법예고 커넥터.

공공데이터포털: 법제처_정부입법예고 / 정부입법현황 OpenAPI
인증키: MOLEG_API_KEY
"""
from __future__ import annotations

import os
from collections.abc import Iterable

import httpx

from ..models import RawBill
from .base import SourceConnector

BASE = "https://apis.data.go.kr/1170000"  # TODO: 실제 서비스 경로로 교체


class MolegNoticeConnector(SourceConnector):
    source_type = "moleg"

    def __init__(self, api_key: str | None = None) -> None:
        self.api_key = api_key or os.environ.get("MOLEG_API_KEY", "")

    def search(self, query: str, *, limit: int = 20) -> Iterable[RawBill]:
        params = {"serviceKey": self.api_key, "numOfRows": limit, "type": "json"}
        resp = httpx.get(f"{BASE}/notice/list", params=params, timeout=20)
        resp.raise_for_status()
        for row in resp.json().get("items", []):
            yield RawBill(
                source_type=self.source_type,
                source_id=str(row.get("id", "")),
                title=row.get("title", ""),
                raw=row,
            )

    def fetch(self, source_id: str) -> RawBill:
        # TODO: 입법예고 상세 조회 엔드포인트 연결
        raise NotImplementedError("입법예고 상세 조회 미구현")
