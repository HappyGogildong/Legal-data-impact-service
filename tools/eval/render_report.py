#!/usr/bin/env python3
"""RAG 오프라인 평가 보고서(JSON) → 같은 이름의 HTML(D83).

    python tools/eval/render_report.py docs/eval/reports/2026-11-21-b6f522d.json \\
        [--baseline core/src/main/resources/eval/offline-baseline.json] [--tolerance 0.02]

표준 라이브러리만 쓴다. HTML 은 파일 하나(외부 요청·스크립트 없음)라 더블클릭으로 연다.
회귀 판정은 실행기 RegressionGate 와 같은 규칙이다 — 두 쪽 시험이
core/src/test/resources/eval/gate-parity.json 을 함께 써서 어긋나지 않게 묶는다.
"""
from __future__ import annotations

import argparse
import html
import json
import re
import sys
from pathlib import Path

DEFAULT_TOLERANCE = 0.02
EPSILON = 1e-9
LOWER_IS_BETTER = frozenset({"discovery.irrelevant", "nomatch.candidate_rate", "refusal.over"})

GROUPS = [
    ("법령 찾기", ["law.resolution", "discovery.hit@3", "discovery.mrr", "discovery.irrelevant"]),
    ("조문", ["article.match"]),
    ("거부·관련 없음", ["refusal.recall", "refusal.over", "nomatch.candidate_rate"]),
    ("의도 분류", ["intent.macro_f1"]),
]
LABELS = {
    "law.resolution": ("법령 이름 해소", "법령 이름을 댄 질문에서 정답 법령 하나로 정한 비율"),
    "discovery.hit@3": ("주제 검색 상위 3", "주제만 말한 질문에서 정답 법령이 후보 상위 3개 안에 든 비율"),
    "discovery.mrr": ("정답 순위 점수(MRR)", "정답 법령 순위의 역수 평균 — 1위 1, 2위 0.5, 못 찾으면 0"),
    "discovery.irrelevant": ("엉뚱한 후보", "주제 질문이 낸 후보 중 정답도 허용 법령도 아닌 비율"),
    "article.match": ("조문 번호", "질문이 지목한 조문 번호를 맞게 뽑은 비율"),
    "refusal.recall": ("거부할 질문 거부", "법령과 무관하거나 없는 법을 물은 질문을 거부한 비율"),
    "refusal.over": ("답할 질문 거부", "답이 있는 질문을 잘못 거부한 비율"),
    "nomatch.candidate_rate": ("관련 없는 질문에 후보", "관련 법령이 없는 질문에 후보를 하나라도 낸 비율"),
    "intent.macro_f1": ("의도 분류 평균 F1", "정답에 등장하는 의도 차원들의 F1 평균"),
}
DIMENSIONS = {"SUMMARY": "요약", "DIFF": "변경점", "IMPACT": "내 영향", "ACTION": "대응", "LOOKUP": "찾기"}
ROUTES = {"RESOLVED": "해소", "AMBIGUOUS": "모호(후보 여럿)", "NOT_FOUND_YET": "미등록", "UNVERIFIED": "확인 불가",
          "OFF_TOPIC": "비법령", "DISCOVERY": "발견"}
CANDIDATES_SHOWN = 5
INTENT_PARTS = ("precision", "recall", "f1")
INTENT_KEY = re.compile(r"^intent\.([A-Z]+)\.(precision|recall|f1)$")
NAME = re.compile(r"^(\d{4}-\d{2}-\d{2})-([0-9a-f]{7})(?:-(\d{6}))?$")


class ReportError(Exception):
    """보고서 파일을 읽을 수 없다."""


def load_report(path: Path) -> dict:
    if not path.is_file():
        raise ReportError(f"파일이 없다: {path}")
    try:
        raw = path.read_bytes()
        # Windows PowerShell 5.1 의 `>` 는 UTF-16 LE(BOM) 로 쓴다
        encoding = "utf-16" if raw[:2] in (b"\xff\xfe", b"\xfe\xff") else "utf-8-sig"
        report = json.loads(raw.decode(encoding))
    except (json.JSONDecodeError, UnicodeDecodeError) as e:
        raise ReportError(f"JSON 이 아니다: {path} ({e})") from e
    metrics = report.get("metrics") if isinstance(report, dict) else None
    if not isinstance(metrics, dict):
        raise ReportError(f"metrics 가 없다: {path}")
    for key, measure in metrics.items():
        if not (isinstance(measure, dict) and isinstance(measure.get("value"), (int, float))
                and isinstance(measure.get("n"), int)):
            raise ReportError(f"지표 형식이 아니다(value·n 필요): {key}")
    return report


# ── 회귀 판정 — RegressionGate 와 같은 규칙 ──────────────────────────────

def allowed(tolerance: float, now_n: int) -> float:
    """허용 오차 — 설정값과 1/분모(지금 보고서) 중 큰 값."""
    return max(tolerance, 1.0 / max(1, now_n))


def worse_by(key: str, now: float, was: float) -> float:
    """나빠진 양. 양수면 나빠졌다."""
    return now - was if key in LOWER_IS_BETTER else was - now


def regressions(report_metrics: dict, baseline_metrics: dict, tolerance: float) -> list[str]:
    """기준선 대비 회귀한 지표 키(기준선 순서). 기준선에 있던 지표가 없어도 회귀다."""
    failed = []
    for key, was in baseline_metrics.items():
        now = report_metrics.get(key)
        if now is None or worse_by(key, now["value"], was["value"]) > allowed(tolerance, now["n"]) + EPSILON:
            failed.append(key)
    return failed


def warnings(report: dict, baseline: dict) -> list[str]:
    """실행기와 같은 경고 — 실패가 아니라 비교의 의미가 약하다는 표시."""
    out = []
    if report.get("config") != baseline.get("config"):
        out.append(f"법령 발견 설정이 기준선과 다르다: {baseline.get('config')} → {report.get('config')}")
    if report.get("indexedLaws") != baseline.get("indexedLaws") or report.get("cases") != baseline.get("cases"):
        out.append(f"색인 법령·문항 수가 기준선과 다르다: {baseline.get('indexedLaws')}건·{baseline.get('cases')}문항"
                   f" → {report.get('indexedLaws')}건·{report.get('cases')}문항")
    return out


# ── 표기 ─────────────────────────────────────────────────────────────

def is_score(key: str) -> bool:
    """비율이 아닌 0~1 점수 — 퍼센트 대신 소수로 쓴다."""
    return key == "discovery.mrr" or key.endswith("f1")


def fmt_value(key: str, measure: dict) -> str:
    value, n = measure["value"], measure["n"]
    if is_score(key):
        return f"{value:.3f} (n={n})"
    return f"{value * 100:.1f}% ({round(value * n)}/{n})"


def fmt_plain(key: str, value: float) -> str:
    return f"{value:.3f}" if is_score(key) else f"{value * 100:.1f}%"


def fmt_amount(key: str, amount: float, signed: bool) -> str:
    if is_score(key):
        return f"{amount:+.3f}" if signed else f"{amount:.3f}"
    return f"{amount * 100:+.1f}%p" if signed else f"{amount * 100:.1f}%p"


def compare(key: str, now: dict | None, was: dict | None, tolerance: float) -> tuple[str, str]:
    """(카드 상태, 비교 문구). 기준선이 없으면 ('', '')."""
    if was is None:
        return "", ""
    if now is None:
        return "regressed", f"기준선 {fmt_value(key, was)} — 이번 보고서에 지표 없음"
    limit = allowed(tolerance, now["n"])
    worse = worse_by(key, now["value"], was["value"])
    text = (f"기준선 {fmt_plain(key, was['value'])} → 변화 {fmt_amount(key, now['value'] - was['value'], True)}"
            f" (허용 {fmt_amount(key, limit, False)})")
    if worse > limit + EPSILON:
        return "regressed", text
    if worse < -EPSILON:
        return "improved", text
    return "steady", text


# ── HTML ────────────────────────────────────────────────────────────

def esc(value) -> str:
    return html.escape(str(value), quote=True)


def card(key: str, now: dict | None, was: dict | None, tolerance: float) -> str:
    label, desc = LABELS.get(key, (key, ""))
    direction = "↓ 낮을수록 좋음" if key in LOWER_IS_BETTER else "↑ 높을수록 좋음"
    state, note = compare(key, now, was, tolerance)
    value = "지표 없음" if now is None else fmt_value(key, now)
    width = 0.0 if now is None else min(1.0, max(0.0, now["value"])) * 100
    parts = [
        f'<article class="card {state}">',
        f"<h3>{esc(label)}</h3>",
        f'<p class="key">{esc(key)} · {direction}</p>',
        f'<p class="value">{esc(value)}</p>',
        f'<div class="bar"><span style="width:{width:.1f}%"></span></div>',
    ]
    if note:
        parts.append(f'<p class="note">{esc(note)}</p>')
    if desc:
        parts.append(f'<p class="desc">{esc(desc)}</p>')
    parts.append("</article>")
    return "\n".join(parts)


def section(title: str, keys: list[str], metrics: dict, base: dict, tolerance: float) -> str:
    present = [k for k in keys if k in metrics or k in base]
    if not present:
        return ""
    cards = "\n".join(card(k, metrics.get(k), base.get(k), tolerance) for k in present)
    return f'<section><h2>{esc(title)}</h2>\n<div class="grid">\n{cards}\n</div></section>'


def intent_table(metrics: dict, base: dict, tolerance: float) -> str:
    dims = sorted({m.group(1) for k in [*metrics, *base] if (m := INTENT_KEY.match(k))},
                  key=lambda d: (list(DIMENSIONS).index(d) if d in DIMENSIONS else len(DIMENSIONS), d))
    if not dims:
        return ""
    rows = []
    for dim in dims:
        cells = []
        for part in INTENT_PARTS:
            key = f"intent.{dim}.{part}"
            now, was = metrics.get(key), base.get(key)
            if now is None and was is None:
                cells.append('<td class="empty">—</td>')
                continue
            state, note = compare(key, now, was, tolerance)
            value = "지표 없음" if now is None else fmt_value(key, now)
            title = f' title="{esc(note)}"' if note else ""
            detail = f'<br><span class="note">{esc(note)}</span>' if note else ""
            cells.append(f'<td class="{state}"{title}>{esc(value)}{detail}</td>')
        rows.append(f"<tr><th>{esc(DIMENSIONS.get(dim, dim))} <span class=\"key\">{esc(dim)}</span></th>{''.join(cells)}</tr>")
    return ('<section><h2>의도 분류 — 차원별</h2><div class="table-wrap"><table>'
            "<thead><tr><th>차원</th><th>정밀도</th><th>재현율</th><th>F1</th></tr></thead>"
            f"<tbody>{''.join(rows)}</tbody></table></div></section>")


def kind_label(row: dict) -> str:
    kind = row.get("kind")
    if kind == "ANSWERABLE":
        return "법령 지목" if row.get("lawNamed") else "주제"
    return {"NO_MATCH": "관련 법령 없음", "REFUSE": "거부 대상"}.get(kind, str(kind))


def candidates_text(ids: list) -> str:
    shown = ", ".join(str(i) for i in ids[:CANDIDATES_SHOWN])
    rest = len(ids) - CANDIDATES_SHOWN
    return f"{shown} 외 {rest}" if rest > 0 else shown


def case_table(rows: list) -> str:
    """문항별 결과 — 실패 행을 강조한다. 옛 보고서(caseResults 없음)면 그리지 않는다."""
    if not rows:
        return ""
    failed = sum(1 for r in rows if not r.get("passed"))
    lines = []
    for i, r in enumerate(rows, 1):
        route = r.get("route")
        route_text = f"{esc(ROUTES.get(route, route))} <span class=\"key\">{esc(route)}</span>" if route else ""
        lines.append(
            f'<tr class="{"pass" if r.get("passed") else "fail"}"><td>{i}</td>'
            f'<td>{"통과" if r.get("passed") else "실패"}</td><td class="wrap">{esc(r.get("query", ""))}</td>'
            f"<td>{esc(kind_label(r))}</td><td>{esc(', '.join(r.get('expectedLawIds') or []))}</td>"
            f"<td>{esc(r.get('lawName') or '')}</td><td>{route_text}</td>"
            f"<td>{esc(candidates_text(r.get('candidateLawIds') or []))}</td></tr>")
    return ("<h2>문항별 결과</h2>"
            f'<p class="sub">실패 {failed}건 / {len(rows)}건 — 판정은 지표와 같다(법령 지목=정답으로 해소, 주제=상위 3, '
            "관련 법령 없음=후보 없음, 거부 대상=거부)</p>"
            '<div class="table-wrap"><table><thead><tr><th>#</th><th>판정</th><th>질문</th><th>종류</th><th>정답</th>'
            "<th>번역 법령명</th><th>경로</th><th>후보</th></tr></thead><tbody>"
            + "".join(lines) + "</tbody></table></div>")


def verdict(report: dict, baseline: dict | None, tolerance: float) -> tuple[str, str, str]:
    if baseline is None:
        return "none", "기준선 없음", "비교할 기준선이 없다 — 이 보고서를 기준선으로 삼을 수 있다"
    failed = regressions(report["metrics"], baseline["metrics"], tolerance)
    if failed:
        return "regressed", f"회귀 {len(failed)}개", ", ".join(failed)
    return "passed", "통과", f"모든 지표가 허용 오차(설정 {tolerance}와 1/분모 중 큰 값) 안"


def headline(name: str) -> str:
    m = NAME.match(name)
    if not m:
        return esc(name)
    date, tag, time = m.groups()
    when = f"{date} {time[:2]}:{time[2:4]}:{time[4:]}" if time else date
    return f"{esc(when)} · 이미지 <code>{esc(tag)}</code>"


CSS = """
:root{--bg:#f6f6f4;--surface:#ffffff;--text:#1d1d1f;--muted:#6b6b70;--border:#e0e0dc;--bar:#4a6fa5;--track:#ececea;
--good:#1f7a4d;--good-bg:#e6f4ec;--bad:#b42318;--bad-bg:#fdecea;--none:#5b5b62;--none-bg:#ececea;--warn-bg:#fff6e0;--warn:#8a5a00}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#141416;--surface:#1e1e21;--text:#ececef;
--muted:#9a9aa2;--border:#34343a;--bar:#7aa2e3;--track:#2a2a2f;--good:#5cc28d;--good-bg:#16301f;--bad:#f07167;
--bad-bg:#3a1c1a;--none:#b4b4bc;--none-bg:#2a2a2f;--warn-bg:#33290f;--warn:#e8b94a}}
:root[data-theme="dark"]{--bg:#141416;--surface:#1e1e21;--text:#ececef;--muted:#9a9aa2;--border:#34343a;--bar:#7aa2e3;
--track:#2a2a2f;--good:#5cc28d;--good-bg:#16301f;--bad:#f07167;--bad-bg:#3a1c1a;--none:#b4b4bc;--none-bg:#2a2a2f;
--warn-bg:#33290f;--warn:#e8b94a}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:15px/1.55 system-ui,-apple-system,"Segoe UI","Apple SD Gothic Neo","Malgun Gothic",sans-serif}
main{max-width:980px;margin:0 auto;padding:24px 16px 48px}
h1{font-size:1.4rem;margin:0 0 4px}h2{font-size:1.05rem;margin:28px 0 10px}h3{font-size:.98rem;margin:0}
code{font-family:ui-monospace,Consolas,monospace}
.sub{color:var(--muted);margin:0 0 16px}
.verdict{border-radius:12px;padding:14px 16px;margin:12px 0;border:1px solid var(--border)}
.verdict strong{font-size:1.15rem;display:block}
.verdict.none{background:var(--none-bg);color:var(--none)}
.verdict.passed{background:var(--good-bg);color:var(--good)}
.verdict.regressed{background:var(--bad-bg);color:var(--bad)}
.meta{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:8px;margin:12px 0}
.meta div{background:var(--surface);border:1px solid var(--border);border-radius:10px;padding:8px 12px}
.meta dt{color:var(--muted);font-size:.8rem}.meta dd{margin:0;font-weight:600}
.warn{background:var(--warn-bg);color:var(--warn);border-radius:10px;padding:10px 14px;margin:8px 0}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:12px}
.card{background:var(--surface);border:1px solid var(--border);border-left:4px solid var(--border);border-radius:12px;padding:12px 14px}
.card.regressed{border-left-color:var(--bad)}.card.improved{border-left-color:var(--good)}
.key{color:var(--muted);font-size:.78rem;margin:2px 0 6px;overflow-wrap:anywhere}
.value{font-size:1.25rem;font-weight:700;margin:4px 0}
.bar{height:6px;background:var(--track);border-radius:3px;overflow:hidden}
.bar span{display:block;height:100%;background:var(--bar)}
.note{font-size:.82rem;margin:8px 0 0}.card.regressed .note{color:var(--bad)}.card.improved .note{color:var(--good)}
.desc{color:var(--muted);font-size:.8rem;margin:6px 0 0}
.table-wrap{overflow-x:auto}
table{border-collapse:collapse;width:100%;background:var(--surface);border-radius:12px;overflow:hidden}
th,td{padding:8px 12px;border-bottom:1px solid var(--border);text-align:left;white-space:nowrap}
tr.fail td{background:var(--bad-bg)}tr.fail td:nth-child(2){color:var(--bad);font-weight:600}
td.wrap{white-space:normal;min-width:220px}
td.regressed{color:var(--bad);font-weight:600}td.improved{color:var(--good);font-weight:600}td.empty{color:var(--muted)}
details{background:var(--surface);border:1px solid var(--border);border-radius:10px;padding:10px 14px;margin:10px 0}
summary{cursor:pointer;font-weight:600}
pre{overflow-x:auto;font-size:.8rem;margin:10px 0 0}
dl.glossary dt{font-weight:600;margin-top:8px}dl.glossary dd{margin:0;color:var(--muted)}
"""


def render_html(report: dict, baseline: dict | None, tolerance: float, name: str) -> str:
    metrics = report["metrics"]
    base = baseline["metrics"] if baseline else {}
    state, title, detail = verdict(report, baseline, tolerance)
    config = report.get("config") or {}
    meta = [
        ("색인 법령", report.get("indexedLaws")), ("평가 문항", report.get("cases")),
        ("제외 문항", report.get("excludedCases")), ("후보 전 청크 수", config.get("chunkFetch")),
        ("최대 후보", config.get("maxCandidates")), ("유사도 하한", config.get("minSimilarity")),
    ]
    grouped = {k for _, keys in GROUPS for k in keys}
    others = sorted(k for k in {*metrics, *base} if k not in grouped and not INTENT_KEY.match(k))
    body = [
        f"<h1>RAG 평가 보고서</h1><p class=\"sub\">{headline(name)}</p>",
        f'<div class="verdict {state}"><strong>{esc(title)}</strong>{esc(detail)}</div>',
        '<dl class="meta">' + "".join(f"<div><dt>{esc(k)}</dt><dd>{esc(v)}</dd></div>" for k, v in meta) + "</dl>",
    ]
    if baseline:
        body += [f'<p class="warn">{esc(w)}</p>' for w in warnings(report, baseline)]
    body += [section(t, keys, metrics, base, tolerance) for t, keys in GROUPS]
    body.append(intent_table(metrics, base, tolerance))
    body.append(section("기타", others, metrics, base, tolerance))
    body.append(case_table(report.get("caseResults") or []))
    glossary = "".join(f"<dt>{esc(label)} <span class=\"key\">{esc(key)}</span></dt><dd>{esc(desc)}</dd>"
                       for key, (label, desc) in LABELS.items())
    body.append("<details><summary>지표 설명·판정 규칙</summary><dl class=\"glossary\">" + glossary + "</dl>"
                f"<p>회귀 = 기준선보다 나빠진 양이 허용 오차를 넘음. 허용 오차 = 설정({esc(tolerance)})과 1/분모 중 큰 값 — "
                "문항이 적은 지표는 한 문항 차이를 우연으로 본다. 기준선에 있던 지표가 사라져도 회귀다.</p></details>")
    body.append("<details><summary>원본 JSON</summary><pre>"
                + esc(json.dumps(report, ensure_ascii=False, indent=2)) + "</pre></details>")
    return ("<!doctype html>\n<html lang=\"ko\"><head><meta charset=\"utf-8\">"
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            f"<title>RAG 평가 보고서 {esc(name)}</title><style>{CSS}</style></head>"
            "<body><main>\n" + "\n".join(part for part in body if part) + "\n</main></body></html>\n")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="RAG 오프라인 평가 보고서(JSON) → 같은 이름의 HTML")
    parser.add_argument("report", type=Path, help="보고서 JSON (lia eval 출력의 '{\"config\"' 한 줄)")
    parser.add_argument("--baseline", type=Path, help="비교할 기준선 JSON")
    parser.add_argument("--tolerance", type=float, default=DEFAULT_TOLERANCE, help="허용 오차(기본 0.02)")
    args = parser.parse_args(argv)
    try:
        report = load_report(args.report)
        baseline = load_report(args.baseline) if args.baseline else None
    except ReportError as e:
        print(f"오류: {e}", file=sys.stderr)
        return 1
    out = args.report.with_suffix(".html")
    out.write_text(render_html(report, baseline, args.tolerance, args.report.stem), encoding="utf-8")
    print(out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
