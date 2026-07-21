"""열린국회 커넥터 스모크 테스트 — 발급 키 검증용.

    python scripts/smoke_assembly.py [검색어]

config.yaml(또는 환경변수)의 assembly.api_key 가 있으면 실 API를 1회 호출한다.
키가 없으면 설정만 점검하고 안내한다(네트워크 호출 없음).
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))
try:  # Windows 콘솔
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

from lia_pipeline.config import build_assembly_connector, get_settings  # noqa: E402


def _mask(key: str) -> str:
    return f"{key[:4]}…({len(key)}자)" if key else "(없음)"


def main() -> None:
    query = sys.argv[1] if len(sys.argv) > 1 else "주택임대차"
    s = get_settings()
    a = s.sources.assembly
    print(f"[config] service={a.service}  base={a.base}")
    print(f"[config] api_key={_mask(a.api_key)}")

    if not a.api_key:
        print("→ 키 없음: config.yaml 의 sources.assembly.api_key 를 채우거나 "
              "환경변수 ASSEMBLY_API_KEY 를 설정하세요. (네트워크 호출 생략)")
        return

    conn = build_assembly_connector(s)
    print(f"[call] search({query!r}, limit=5) …")
    try:
        rows = list(conn.search(query, limit=5))
        print(f"[ok] {len(rows)}건")
        for r in rows:
            print(f"   - [{r.bill_no}] {r.title}")
        if not rows:
            print("   (0건 — service 이름/필드명이 실제 서비스와 맞는지 확인)")
    except Exception as e:  # 인증/서비스명/네트워크 오류 진단
        print(f"[error] {type(e).__name__}: {e}")
        print("   → api_key·service·필드명(assembly_bills._to_raw)을 콘솔 문서로 확인하세요.")


if __name__ == "__main__":
    main()
