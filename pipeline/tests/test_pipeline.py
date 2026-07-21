"""오프라인 단위 테스트 — 네트워크/키 불필요.

실행: `python tests/test_pipeline.py`  또는  `pytest`
"""
from __future__ import annotations

import os
import sys
from collections.abc import Iterable

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))

from lia_pipeline.connectors.assembly_bills import (  # noqa: E402
    AssemblyApiError, _check_result, _extract_rows, _result_code,
)
from lia_pipeline.connectors.base import SourceConnector  # noqa: E402
from lia_pipeline.ingest.source_analyzer import (  # noqa: E402
    ResolutionState, SourceAnalyzer,
)
from lia_pipeline.models import RawBill  # noqa: E402


# --- 열린국회 봉투 파싱 -------------------------------------------------
def _payload(rows, code="INFO-000"):
    return {"SVC": [
        {"head": [{"list_total_count": len(rows)}, {"RESULT": {"CODE": code, "MESSAGE": "x"}}]},
        {"row": rows},
    ]}


def test_extract_rows():
    rows = [{"BILL_NO": "1"}, {"BILL_NO": "2"}]
    assert _extract_rows(_payload(rows)) == rows
    assert _extract_rows({"SVC": []}) == []


def test_result_code_and_error():
    code, _ = _result_code(_payload([], code="INFO-200"))
    assert code == "INFO-200"
    _check_result(_payload([]))  # INFO-000 → 통과
    try:
        _check_result(_payload([], code="ERROR-300"))
        assert False, "ERROR 코드는 예외여야 함"
    except AssemblyApiError:
        pass


# --- SourceAnalyzer 4상태 ---------------------------------------------
class FakeConn(SourceConnector):
    source_type = "fake"
    DATA = [
        ("PRC1", "2200001", "주택임대차보호법 일부개정법률안"),
        ("PRC2", "2200002", "주택임대차보호법 일부개정법률안"),  # 동명(현실에서 흔함)
        ("PRC3", "2200010", "소득세법 일부개정법률안"),
    ]

    def search(self, query: str, *, limit: int = 20) -> Iterable[RawBill]:
        return [self._raw(*d) for d in self.DATA
                if query and any(t in d[2] for t in query.split())][:limit]

    def fetch(self, source_id: str) -> RawBill:
        for d in self.DATA:
            if d[0] == source_id:
                return self._raw(*d)
        raise LookupError(source_id)

    def get_by_bill_no(self, bill_no: str):
        for d in self.DATA:
            if d[1] == str(bill_no):
                return self._raw(*d)
        return None

    def _raw(self, sid, no, title):
        return RawBill(source_type=self.source_type, source_id=sid, bill_no=no, title=title)


def test_resolution_states():
    sa = SourceAnalyzer([FakeConn()])
    assert sa.resolve("의안번호 2200001").state is ResolutionState.RESOLVED
    assert sa.resolve("의안번호 9999999").state is ResolutionState.NOT_FOUND_YET
    assert sa.resolve("소득세법 일부개정법률안").state is ResolutionState.RESOLVED
    assert sa.resolve("주택임대차보호법 일부개정법률안").state is ResolutionState.AMBIGUOUS
    assert sa.resolve("아직 발의 안 된 가상의 무슨무슨 법률안").state is ResolutionState.NOT_FOUND_YET
    assert sa.resolve("오늘 점심 뭐 먹지").state is ResolutionState.UNVERIFIED


# --- 설정 로딩(YAML + 보간) -------------------------------------------
def test_config_yaml_and_interpolation(tmp_path=None):
    import tempfile
    from lia_pipeline.config import load_settings

    os.environ["ASSEMBLY_API_KEY"] = "ENVKEY123"
    yaml_text = (
        "sources:\n"
        "  assembly:\n"
        "    api_key: \"${ASSEMBLY_API_KEY}\"\n"      # 환경변수 보간
        "    service: \"svc_test\"\n"
        "embedding:\n"
        "  provider: \"upstage\"\n"
        "  api_key: \"direct-literal-key\"\n"           # 직접 문자열
    )
    d = tempfile.mkdtemp()
    p = os.path.join(d, "config.yaml")
    with open(p, "w", encoding="utf-8") as f:
        f.write(yaml_text)
    s = load_settings(p)
    assert s.sources.assembly.api_key == "ENVKEY123"      # 보간됨
    assert s.sources.assembly.service == "svc_test"
    assert s.embedding.provider == "upstage"
    assert s.embedding.api_key == "direct-literal-key"    # 직접값
    # 미설정 보간은 빈 문자열 (법제처 계열은 OC 사용)
    os.environ.pop("MOLEG_OC", None)
    assert s.sources.moleg.oc == ""


def test_config_missing_file_defaults():
    from lia_pipeline.config import load_settings
    s = load_settings("/no/such/config.yaml")
    assert s.sources.assembly.service == "nzmimeepazxkubdpn"  # 기본값
    assert s.sources.assembly.api_key == ""


def test_failclosed_no_connector():
    # 커넥터 오류/무응답이어도(여기선 빈 커넥터) 지어내지 않고 거부 상태
    sa = SourceAnalyzer([])
    assert sa.resolve("무슨무슨 법률안").state is ResolutionState.NOT_FOUND_YET
    assert sa.resolve("점심 메뉴 추천").state is ResolutionState.UNVERIFIED


if __name__ == "__main__":
    fns = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    for fn in fns:
        fn()
        print(f"PASS {fn.__name__}")
    print(f"\n{len(fns)} passed")
