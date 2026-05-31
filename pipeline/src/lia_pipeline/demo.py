"""수직 슬라이스 데모: 출처 검색 → 정규화 → 영향 요약.

    python -m lia_pipeline.demo

API 키가 없으면 네트워크 호출은 건너뛰고 구조만 시연한다.
"""
from __future__ import annotations

from .analysis import AnalysisEngine
from .connectors import AssemblyBillsConnector
from .ingest.normalizer import normalize
from .ingest.source_analyzer import SourceAnalyzer
from .models import Bill, Stage


def main() -> None:
    connectors = [AssemblyBillsConnector()]
    analyzer = SourceAnalyzer(connectors)
    engine = AnalysisEngine()

    sample_url = "https://example.com/news/some-bill-article"
    print(f"[1] 소스 입력 분석: {sample_url}")
    try:
        result = analyzer.resolve(sample_url)
        print(f"    matched={result.matched}, 확인필요={result.needs_confirmation}")
    except Exception as e:  # 키/네트워크 없을 때
        print(f"    (네트워크/키 없음 — 스킵: {e})")

    print("[2] 정규화 데모 (모의 RawBill 대신 표준 Bill 직접 구성)")
    bill = Bill(
        id="assembly:DEMO",
        bill_no="2200001",
        title="주택임대차보호법 일부개정법률안",
        stage=Stage.COMMITTEE,
        source_type="assembly",
    )
    print(f"    {bill.title} / 단계={bill.stage.value}")

    print("[3] 영향 요약 (페르소나=임차인)")
    impact = engine.summarize(bill, persona="임차인")
    print(f"    summary={impact.summary}")
    print(f"    actions={impact.actions}")


if __name__ == "__main__":
    main()
