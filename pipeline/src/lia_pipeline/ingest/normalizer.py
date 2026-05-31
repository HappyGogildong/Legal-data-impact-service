"""RawBill → 표준 Bill 정규화.

출처별 필드 매핑을 여기 한 곳에 모은다. 커넥터는 원자료만, 의미 부여는 여기서.
"""
from __future__ import annotations

from ..models import Bill, RawBill, Stage


def normalize(raw: RawBill) -> Bill:
    if raw.source_type == "assembly":
        return _from_assembly(raw)
    if raw.source_type == "moleg":
        return _from_moleg(raw)
    raise ValueError(f"알 수 없는 출처: {raw.source_type}")


def _from_assembly(raw: RawBill) -> Bill:
    r = raw.raw
    return Bill(
        id=f"assembly:{raw.source_id}",
        bill_no=raw.bill_no,
        title=raw.title,
        proposers=_split(r.get("PROPOSER")),
        committee=r.get("COMMITTEE"),
        stage=_map_stage(r.get("PROC_RESULT")),
        source_type=raw.source_type,
        source_url=r.get("DETAIL_LINK"),
    )


def _from_moleg(raw: RawBill) -> Bill:
    r = raw.raw
    return Bill(
        id=f"moleg:{raw.source_id}",
        title=raw.title,
        stage=Stage.PROPOSED,
        source_type=raw.source_type,
        source_url=r.get("link"),
    )


def _split(value: str | None) -> list[str]:
    return [p.strip() for p in value.split(",")] if value else []


def _map_stage(proc_result: str | None) -> Stage:
    # TODO: 실제 처리상태 문자열 → Stage 매핑 테이블 정교화
    if not proc_result:
        return Stage.PROPOSED
    if "공포" in proc_result:
        return Stage.PROMULGATED
    if "본회의" in proc_result:
        return Stage.PLENARY
    return Stage.COMMITTEE
