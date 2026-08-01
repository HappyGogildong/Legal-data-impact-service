"""시행예정 법령(eflaw) 응답 구조 실측 — RawLaw 필드 정의 근거.

  python tools/probe_eflaw.py            # 기본(주택법 MST=283191)
  python tools/probe_eflaw.py <MST> <efYd>
"""
import sys, json, pathlib, re, httpx

ENV = dict(l.split("=", 1) for l in pathlib.Path(".env").read_text(encoding="utf-8").splitlines()
           if "=" in l and not l.startswith("#"))
OC = ENV["LAW_OC"].strip()
BASE = "https://www.law.go.kr/DRF"


def sec(t): print(f"\n{'='*68}\n{t}\n{'='*68}")


def strip(x):
    s = "\n".join(strip(i) for i in x) if isinstance(x, list) else \
        "\n".join(strip(v) for v in x.values()) if isinstance(x, dict) else str(x)
    return re.sub(r"\n{2,}", "\n", re.sub(r"<[^>]+>", "", s)).strip()


def search(**extra):
    p = {"OC": OC, "target": "eflaw", "type": "JSON", **extra}
    r = httpx.get(f"{BASE}/lawSearch.do", params=p, timeout=30)
    r.raise_for_status()
    j = r.json()
    return j[list(j.keys())[0]]


def main():
    mst = sys.argv[1] if len(sys.argv) > 1 else "283191"
    efyd = sys.argv[2] if len(sys.argv) > 2 else "20260804"

    # 1) 목록 — 필드 전수
    sec("1. 목록 lawSearch.do?target=eflaw  (시행예정 필터)")
    body = search(efYd="20260802~20271231", sort="efasc", display="5")
    print(f"totalCnt = {body.get('totalCnt')}  (2026-08-02~2027-12-31 시행예정)")
    rows = body.get("law") or []
    rows = [rows] if isinstance(rows, dict) else rows
    print(f"\n목록 필드 {len(rows[0])}개:")
    for k, v in rows[0].items():
        print(f"  {k:16s} = {str(v)[:80]}")

    # 페이징 상한
    sec("2. 페이징 상한 (numOfRows)")
    for n in (100, 200):
        b = search(efYd="20260802~20271231", display=str(n))
        got = b.get("law") or []
        print(f"  display={n:4d} → 반환 {len(got) if isinstance(got, list) else 1}건")

    # 3) 본문 구조
    sec(f"3. 본문 lawService.do?target=eflaw&MST={mst}&efYd={efyd}")
    d = httpx.get(f"{BASE}/lawService.do",
                  params={"OC": OC, "target": "eflaw", "MST": mst, "type": "JSON", "efYd": efyd},
                  timeout=30)
    root = d.json()["법령"]
    print(f"응답 {len(d.text):,}B · 최상위 키: {list(root.keys())}")

    print("\n[기본정보] 필드:")
    for k, v in root["기본정보"].items():
        print(f"  {k:16s} = {strip(v)[:70] if not isinstance(v,(str,int)) else str(v)[:70]}")

    arts = root.get("조문", {}).get("조문단위", [])
    arts = [arts] if isinstance(arts, dict) else arts
    print(f"\n[조문] 조문단위 {len(arts)}개 · 필드: {list(arts[0].keys())}")
    real = [a for a in arts if a.get("조문여부") == "조문"] or arts
    print(f"  조문여부='조문' {len(real)}건 / 전개(장·절 제목 등) {len(arts)-len(real)}건")
    a = real[1] if len(real) > 1 else real[0]
    for k, v in a.items():
        if k == "항": continue
        print(f"    {k:14s} = {str(v)[:70]}")
    if "항" in a:
        hangs = a["항"] if isinstance(a["항"], list) else [a["항"]]
        print(f"    항 {len(hangs)}개 · 필드: {list(hangs[0].keys())}")
        print(f"      예: {strip(hangs[0])[:100]}")

    ad = root.get("부칙", {}).get("부칙단위", [])
    ad = [ad] if isinstance(ad, dict) else ad
    print(f"\n[부칙] 부칙단위 {len(ad)}개 · 필드: {list(ad[0].keys()) if ad else '없음'}")
    if ad: print(f"  최신: {strip(ad[0])[:220]}")

    for key in ("제개정이유", "개정문"):
        t = strip(root.get(key, ""))
        print(f"\n[{key}] {len(t):,}자 · 앞 160자: {t[:160]}")

    # 4) 현행본과의 연결 (diff 기준선)
    sec("4. diff 기준선 — 같은 법령의 현행본(target=law)")
    name = root["기본정보"].get("법령명_한글")
    cur = httpx.get(f"{BASE}/lawSearch.do",
                    params={"OC": OC, "target": "law", "type": "JSON", "query": name, "display": "3"},
                    timeout=30).json()
    cb = cur[list(cur.keys())[0]]
    crows = cb.get("law") or []
    crows = [crows] if isinstance(crows, dict) else crows
    for c in crows[:3]:
        print(f"  현행 MST={c.get('법령일련번호')} 시행={c.get('시행일자')} 공포={c.get('공포일자')} "
              f"법령ID={c.get('법령ID')} {c.get('법령명한글')}")
    print(f"  시행예정 법령ID={root['기본정보'].get('법령ID')} MST={mst}")
    print("  → 법령ID 동일 여부가 현행↔시행예정 연결 키인지 확인")


if __name__ == "__main__":
    main()
