"""수직 슬라이스 데모: 소스 해소(4상태) → 정규화 → 영향 요약.

    python -m lia_pipeline.demo

네트워크/키 없이도 돌도록 오프라인 FakeConnector로 SourceAnalyzer를 시연한다.
실제 수집은 AssemblyBillsConnector(ASSEMBLY_API_KEY 필요)로 교체.
"""
from __future__ import annotations

import sys
from collections.abc import Iterable

try:  # Windows 콘솔(cp949)에서도 한글·기호 출력
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

from .analysis import AnalysisEngine
from .config import build_assembly_connector, get_settings
from .connectors.base import SourceConnector
from .ingest.source_analyzer import SourceAnalyzer
from .models import Bill, RawBill, Stage


class _FakeConnector(SourceConnector):
    """오프라인 데모용 — 메모리 내 의안 목록."""
    source_type = "fake"

    _DATA = [
        {"BILL_ID": "PRC_A1", "BILL_NO": "2200001", "BILL_NAME": "주택임대차보호법 일부개정법률안"},
        {"BILL_ID": "PRC_A2", "BILL_NO": "2200002", "BILL_NAME": "주택임대차보호법 일부개정법률안"},
        {"BILL_ID": "PRC_B1", "BILL_NO": "2200010", "BILL_NAME": "소득세법 일부개정법률안"},
    ]

    def search(self, query: str, *, limit: int = 20) -> Iterable[RawBill]:
        out = []
        for r in self._DATA:
            if query and any(tok in r["BILL_NAME"] for tok in query.split()):
                out.append(self._raw(r))
        return out[:limit]

    def fetch(self, source_id: str) -> RawBill:
        for r in self._DATA:
            if r["BILL_ID"] == source_id:
                return self._raw(r)
        raise LookupError(source_id)

    def get_by_bill_no(self, bill_no: str) -> RawBill | None:
        for r in self._DATA:
            if r["BILL_NO"] == str(bill_no):
                return self._raw(r)
        return None

    def _raw(self, r: dict) -> RawBill:
        return RawBill(source_type=self.source_type, source_id=r["BILL_ID"],
                       bill_no=r["BILL_NO"], title=r["BILL_NAME"], raw=r)


def main() -> None:
    settings = get_settings()
    if settings.sources.assembly.api_key:
        print("[mode] AssemblyBillsConnector — config.yaml 의 열린국회 키 사용 (실 API 호출)")
        connectors: list[SourceConnector] = [build_assembly_connector(settings)]
    else:
        print("[mode] 오프라인 FakeConnector — config.yaml/키 없음")
        connectors = [_FakeConnector()]
    analyzer = SourceAnalyzer(connectors)
    engine = AnalysisEngine()

    print("[1] 소스 해소 - 4상태 시연")
    cases = [
        ("의안번호 2200001", "의안번호 정확 -> RESOLVED 기대"),
        ("소득세법 일부개정법률안", "정확 법안명 -> RESOLVED 기대"),
        ("주택임대차보호법 일부개정법률안", "동명 다수 -> AMBIGUOUS 기대"),
        ("아직 발의 안 된 가상의 무슨무슨 법률안", "미등록 -> NOT_FOUND_YET 기대"),
        ("오늘 점심 뭐 먹지", "법안 아님 -> UNVERIFIED 기대"),
    ]
    for value, note in cases:
        res = analyzer.resolve(value)
        tgt = res.resolved.title if res.resolved else (
            f"{len(res.candidates)}개 후보" if res.candidates else "-")
        print(f"    - {value!r:40} -> {res.state.value:14} ({tgt})  // {note}")

    print("\n[2] 영향 요약 (페르소나=임차인)")
    bill = Bill(id="fake:PRC_A1", bill_no="2200001",
                title="주택임대차보호법 일부개정법률안", stage=Stage.COMMITTEE, source_type="fake")
    impact = engine.summarize(bill, persona="임차인")
    print(f"    summary={impact.summary}")
    print(f"    actions={impact.actions}")


if __name__ == "__main__":
    main()
