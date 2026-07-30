"""출처 연동 진단 — "어떤 형식으로 법안을 가져올 수 있는가"를 실측한다.

    python scripts/probe_sources.py [검색어]

목적 (D38: 본문 획득 경로 미확정):
  1) 설정된 자격증명 상태 확인
  2) 열린국회 목록 API가 주는 필드 실측
  3) 본문(제안이유·조문·부칙·신구조문대비표) 제공 서비스 탐색
  4) 국가법령정보(현행법) 응답 형식 확인 — OC 있을 때만

읽기 전용. 결과는 docs/components/SourceConnector.md §본문 획득 갱신 근거로 쓴다.
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

import httpx  # noqa: E402

from lia_pipeline.config import get_settings  # noqa: E402

# 본문 판정은 필드명 힌트가 아니라 **값 길이**로 한다.
# (PPSR_CN="김기표의원 등 11인"처럼 CN/CONTENT 이름이 붙어도 본문이 아닌 경우가 많아 오탐)
BODY_MIN_CHARS = 200


def _mask(v: str) -> str:
    return f"{v[:4]}…({len(v)}자)" if v else "(미설정)"


def _rows(payload: dict) -> list[dict]:
    """열린국회 [head,row] 봉투에서 row 추출."""
    for v in payload.values():
        if isinstance(v, list):
            for it in v:
                if isinstance(it, dict) and "row" in it:
                    return it["row"]
    return []


def _result(payload: dict) -> tuple[str | None, str | None]:
    top = payload.get("RESULT")
    if isinstance(top, dict):
        return top.get("CODE"), top.get("MESSAGE")
    for v in payload.values():
        if isinstance(v, list):
            for it in v:
                if isinstance(it, dict) and "head" in it:
                    for h in it["head"]:
                        if isinstance(h, dict) and "RESULT" in h:
                            r = h["RESULT"]
                            return r.get("CODE"), r.get("MESSAGE")
    return None, None


def section(title: str) -> None:
    print(f"\n{'=' * 62}\n{title}\n{'=' * 62}")


def probe_credentials(s) -> None:
    section("1. 자격증명 상태")
    a = s.sources.assembly
    print(f"  assembly  key={_mask(a.api_key)}  service={a.service}  AGE={a.age}")
    print(f"  moleg     oc ={_mask(s.sources.moleg.oc)}   (법제처 입법예고)")
    print(f"  law       oc ={_mask(s.sources.law.oc)}   (국가법령정보)")
    print(f"  embedding key={_mask(s.embedding.api_key)}  llm key={_mask(s.llm.api_key)}")


def probe_assembly_list(s, query: str) -> dict | None:
    """목록 API 형식 실측 — 어떤 필드를 주는가."""
    section("2. 열린국회 목록 API — 제공 필드 실측")
    a = s.sources.assembly
    if not a.api_key:
        print("  (키 없음 — 생략)")
        return None
    r = httpx.get(f"{a.base}/{a.service}",
                  params={"KEY": a.api_key, "Type": "json", "AGE": a.age,
                          "pIndex": 1, "pSize": 1, "BILL_NAME": query},
                  timeout=20)
    payload = r.json()
    code, msg = _result(payload)
    rows = _rows(payload)
    print(f"  HTTP {r.status_code} / RESULT={code} / rows={len(rows)}")
    if not rows:
        print(f"  {msg}")
        return None
    row = rows[0]
    print(f"  필드 {len(row)}개: {', '.join(row.keys())}")
    print(f"\n  샘플: [{row.get('BILL_NO')}] {row.get('BILL_NAME')}")
    print(f"        위원회={row.get('COMMITTEE')} 발의일={row.get('PROPOSE_DT')} 제안자={row.get('PROPOSER')}")
    long_fields = [(k, len(str(v))) for k, v in row.items()
                   if v and len(str(v)) > BODY_MIN_CHARS]
    print(f"\n  → 본문 후보(>{BODY_MIN_CHARS}자) 필드: {long_fields or '없음 ❌ (메타데이터 전용)'}")
    return row


def probe_body_services(s, bill_id: str) -> None:
    """본문 제공 서비스 탐색 (D38 후보 1 검증)."""
    section("3. 본문(제안이유·조문) 제공 서비스 탐색 — D38")
    a = s.sources.assembly
    if not (a.api_key and bill_id):
        print("  (키/BILL_ID 없음 — 생략)")
        return
    # 알려진/추정 서비스 ID 후보. 콘솔에서 확인되면 여기에 추가.
    candidates = [
        ("nzmimeepazxkubdpn", "발의법률안 목록(현행 사용)"),
        ("BILLINFODETAIL", "의안 상세(심사 단계 이력)"),
        ("BILLINFOPPSR", "의안 제안자"),
        ("TVBPMBILL11", "의안 통합"),
        ("BILLRCP", "의안 접수"),
    ]
    found = False
    for svc, label in candidates:
        try:
            r = httpx.get(f"{a.base}/{svc}",
                          params={"KEY": a.api_key, "Type": "json", "pIndex": 1,
                                  "pSize": 1, "BILL_ID": bill_id, "AGE": a.age},
                          timeout=15)
            payload = r.json()
            code, msg = _result(payload)
            rows = _rows(payload)
            if not rows:
                print(f"  {svc:20} [{label}] → {code} {str(msg)[:30]}")
                continue
            long_fields = [(k, len(str(v))) for k, v in rows[0].items()
                           if v and len(str(v)) > BODY_MIN_CHARS]
            mark = f"✅ 본문 후보 {long_fields}" if long_fields else "메타만 (본문 없음)"
            print(f"  {svc:20} [{label}] → rows={len(rows)} 필드{len(rows[0])}개 {mark}")
            if long_fields:
                found = True
        except Exception as e:
            print(f"  {svc:20} [{label}] → EXC {type(e).__name__}")
    if not found:
        print("\n  → ❌ 본문 제공 서비스 미발견. D38 후보 ②(HWP 첨부+hwplib) 검토 필요.")
        print("     콘솔(open.assembly.go.kr)에서 '의안 제안이유' 검색해 서비스 ID 확인 권장.")


def probe_law(s) -> None:
    """국가법령정보 — 현행법 응답 형식 (diff 기준선)."""
    section("4. 국가법령정보(현행법) — OC 필요")
    oc = s.sources.law.oc
    if not oc:
        print("  (OC 미설정 — 생략) 회원 이메일의 아이디 부분을 LAW_OC 로 설정")
        return
    try:
        r = httpx.get(f"{s.sources.law.base}/DRF/lawSearch.do",
                      params={"OC": oc, "target": "law", "type": "JSON",
                              "query": "주택임대차보호법", "display": 1},
                      timeout=20)
        print(f"  HTTP {r.status_code}, {len(r.text):,} bytes")
        ct = r.headers.get("content-type", "")
        print(f"  content-type: {ct}")
        print(f"  응답 앞부분: {r.text[:300]}")
    except Exception as e:
        print(f"  EXC {type(e).__name__}: {e}")


def main() -> None:
    query = sys.argv[1] if len(sys.argv) > 1 else "주택임대차"
    s = get_settings()
    probe_credentials(s)
    row = probe_assembly_list(s, query)
    probe_body_services(s, (row or {}).get("BILL_ID", ""))
    probe_law(s)
    section("요약")
    print("  · 목록 API: 메타데이터만 → Normalizer Phase 1(필드매핑+revision) 가능")
    print("  · 본문: 경로 미확정(D38) → Phase 2(조문·부칙·대비표 파싱) 보류")
    print("  · 상세: docs/components/SourceConnector.md §본문 획득")


if __name__ == "__main__":
    main()
