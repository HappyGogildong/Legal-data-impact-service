"""열린국회정보 OpenAPI 커넥터 — 발의 법률안 (신뢰 출처 수집).

엔드포인트: https://open.assembly.go.kr/portal/openapi/{SERVICE}
인증키: ASSEMBLY_API_KEY (open.assembly.go.kr 발급)
설계: docs/components/SourceConnector.md

응답 봉투(열린국회 공통):
    { "<SERVICE>": [ {"head":[{"list_total_count":N},{"RESULT":{"CODE":"INFO-000",...}}]},
                     {"row":[ {...}, ... ]} ] }

⚠️ 서비스명(SERVICE)·필드명(BILL_ID/BILL_NO/...)은 서비스마다 다르다.
   실제 키 발급 후 해당 서비스 문서로 `DEFAULT_SERVICE`와 `_to_raw` 매핑을 검증할 것.
   (서비스명은 ASSEMBLY_BILL_SERVICE 환경변수로도 주입 가능)
"""
from __future__ import annotations

import os
import time
from collections.abc import Iterable

import httpx

from ..models import RawBill
from .base import SourceConnector

BASE = "https://open.assembly.go.kr/portal/openapi"
#: 발의 법률안 목록 서비스(예시). 키 발급 후 콘솔에서 확인·교체 권장.
DEFAULT_SERVICE = "nzmimeepazxkubdpn"


class AssemblyApiError(RuntimeError):
    """열린국회 API가 ERROR 코드를 반환했을 때."""


class AssemblyBillsConnector(SourceConnector):
    source_type = "assembly"

    def __init__(
        self,
        api_key: str | None = None,
        *,
        service: str | None = None,
        age: str = "22",
        base: str = BASE,
        page_size: int = 100,
        timeout: float = 20.0,
        max_retries: int = 3,
    ) -> None:
        self.api_key = api_key or os.environ.get("ASSEMBLY_API_KEY", "")
        self.service = service or os.environ.get("ASSEMBLY_BILL_SERVICE", DEFAULT_SERVICE)
        self.age = age                      # 국회 대수(AGE) — 이 서비스의 필수 파라미터
        self.base = base
        self.page_size = page_size
        self.timeout = timeout
        self.max_retries = max_retries

    # --- 공개 API ------------------------------------------------------
    def search(self, query: str, *, limit: int = 20) -> Iterable[RawBill]:
        """법안명 키워드로 목록 조회(페이징 누적)."""
        self._require_key()
        collected: list[RawBill] = []
        pindex = 1
        while len(collected) < limit:
            psize = min(self.page_size, limit - len(collected))
            payload = self._request({"BILL_NAME": query}, pindex=pindex, psize=psize)
            rows = _extract_rows(payload)
            if not rows:
                break
            collected.extend(self._to_raw(r) for r in rows)
            if len(rows) < psize:  # 마지막 페이지
                break
            pindex += 1
        return collected[:limit]

    def get_by_bill_no(self, bill_no: str) -> RawBill | None:
        """의안번호 정확 조회(전용 파라미터 사용)."""
        self._require_key()
        payload = self._request({"BILL_NO": bill_no}, pindex=1, psize=10)
        for row in _extract_rows(payload):
            if str(row.get("BILL_NO", "")) == str(bill_no):
                return self._to_raw(row)
        return None

    def fetch(self, source_id: str) -> RawBill:
        """BILL_ID로 단건 조회. (의안 원문 전문은 별도 서비스 — TODO)"""
        self._require_key()
        payload = self._request({"BILL_ID": source_id}, pindex=1, psize=5)
        rows = _extract_rows(payload)
        if rows:
            return self._to_raw(rows[0])
        raise LookupError(f"BILL_ID={source_id} 조회 결과 없음")

    # --- 내부 ----------------------------------------------------------
    def _require_key(self) -> None:
        if not self.api_key:
            raise RuntimeError("ASSEMBLY_API_KEY 미설정 — .env에 키를 넣으세요.")

    def _request(self, extra: dict, *, pindex: int, psize: int) -> dict:
        params = {
            "KEY": self.api_key,
            "Type": "json",
            "AGE": self.age,        # 필수 파라미터
            "pIndex": pindex,
            "pSize": psize,
            **extra,
        }
        url = f"{self.base}/{self.service}"
        last_exc: Exception | None = None
        for attempt in range(self.max_retries):
            try:
                resp = httpx.get(url, params=params, timeout=self.timeout)
                resp.raise_for_status()
                payload = resp.json()
                _check_result(payload)  # ERROR 코드면 예외
                return payload
            except httpx.HTTPStatusError as e:
                if e.response is not None and e.response.status_code >= 500:
                    last_exc = e
                else:
                    raise
            except httpx.TransportError as e:  # 연결/타임아웃
                last_exc = e
            time.sleep(0.5 * (2**attempt))  # 지수 백오프
        assert last_exc is not None
        raise last_exc

    def _to_raw(self, row: dict) -> RawBill:
        """출처 행 → RawBill. 필드명은 서비스 문서로 검증·보정할 것."""
        return RawBill(
            source_type=self.source_type,
            source_id=str(row.get("BILL_ID") or row.get("BILL_NO") or ""),
            bill_no=row.get("BILL_NO"),
            title=row.get("BILL_NAME") or row.get("TITLE") or "",
            raw=row,
        )


def _extract_rows(payload: dict) -> list[dict]:
    """열린국회 특유의 [head, row] 중첩 구조에서 row만 뽑는다(없으면 [])."""
    for value in payload.values():
        if isinstance(value, list):
            for item in value:
                if isinstance(item, dict) and "row" in item:
                    return item["row"]
    return []


def _check_result(payload: dict) -> None:
    """head의 RESULT.CODE 확인. ERROR-* 면 예외, INFO-200(데이터없음)은 정상 취급."""
    code, message = _result_code(payload)
    if code and code.startswith("ERROR"):
        raise AssemblyApiError(f"{code}: {message}")


def _result_code(payload: dict) -> tuple[str | None, str | None]:
    # 오류 응답은 최상위 {"RESULT": {CODE, MESSAGE}} 형태로 온다.
    top = payload.get("RESULT")
    if isinstance(top, dict):
        return top.get("CODE"), top.get("MESSAGE")
    # 정상 응답은 서비스 봉투 안 head[].RESULT
    for value in payload.values():
        if isinstance(value, list):
            for item in value:
                if isinstance(item, dict) and "head" in item:
                    for h in item["head"]:
                        if isinstance(h, dict) and "RESULT" in h:
                            r = h["RESULT"]
                            return r.get("CODE"), r.get("MESSAGE")
    return None, None
