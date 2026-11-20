"""render_report.py 시험 — 저장소 루트에서: python -m unittest discover -s tools/eval -v"""
import json
import shutil
import tempfile
import unittest
from pathlib import Path

import render_report as rr

ROOT = Path(__file__).resolve().parents[2]
PARITY = ROOT / "core" / "src" / "test" / "resources" / "eval" / "gate-parity.json"

METRICS = {
    "discovery.hit@3": {"value": 0.5, "n": 14},
    "refusal.over": {"value": 0.0, "n": 28},
    "intent.DIFF.f1": {"value": 0.8, "n": 5},
    "intent.macro_f1": {"value": 0.75, "n": 14},
    "new<b>.metric": {"value": 0.1, "n": 3},
}


def report(metrics, **overrides):
    body = {"config": {"chunkFetch": 40, "maxCandidates": 10, "minSimilarity": 0.3},
            "indexedLaws": 900, "cases": 39, "excludedCases": 0, "metrics": metrics}
    body.update(overrides)
    return body


class GateParityTest(unittest.TestCase):
    """실행기 RegressionGate 와 같은 픽스처로 같은 판정을 내는가(Java: RegressionGateParityTest)."""

    def setUp(self):
        self.fixture = json.loads(PARITY.read_text(encoding="utf-8"))

    def test_낮을수록_좋은_지표가_같다(self):
        self.assertEqual(set(self.fixture["lowerIsBetter"]), set(rr.LOWER_IS_BETTER))

    def test_사례별_회귀_지표가_같다(self):
        for case in self.fixture["cases"]:
            with self.subTest(case["name"]):
                got = rr.regressions(case["report"], case["baseline"], case["tolerance"])
                self.assertEqual(sorted(case["expectedFailures"]), sorted(got))


class RenderTest(unittest.TestCase):

    def test_기준선_없음(self):
        page = rr.render_html(report(METRICS), None, 0.02, "2026-11-21-b6f522d")
        self.assertIn('class="verdict none"', page)
        self.assertIn("2026-11-21", page)
        self.assertIn("b6f522d", page)

    def test_회귀(self):
        baseline = report({"discovery.hit@3": {"value": 0.9, "n": 14}})
        page = rr.render_html(report(METRICS), baseline, 0.02, "x")
        self.assertIn('class="verdict regressed"', page)
        self.assertIn("회귀 1개", page)
        self.assertIn('class="card regressed"', page)

    def test_통과(self):
        baseline = report({"discovery.hit@3": {"value": 0.5, "n": 14}})
        page = rr.render_html(report(METRICS), baseline, 0.02, "x")
        self.assertIn('class="verdict passed"', page)

    def test_개선은_초록(self):
        baseline = report({"discovery.hit@3": {"value": 0.2, "n": 14}})
        page = rr.render_html(report(METRICS), baseline, 0.02, "x")
        self.assertIn('class="card improved"', page)

    def test_기준선_지표가_없어지면_회귀_카드(self):
        baseline = report({"article.match": {"value": 1.0, "n": 5}})
        page = rr.render_html(report(METRICS), baseline, 0.02, "x")
        self.assertIn('class="verdict regressed"', page)
        self.assertIn('class="card regressed"', page)
        self.assertIn("지표 없음", page)

    def test_설정이_다르면_경고(self):
        baseline = report({}, config={"chunkFetch": 20, "maxCandidates": 10, "minSimilarity": 0.3})
        page = rr.render_html(report(METRICS), baseline, 0.02, "x")
        self.assertIn("법령 발견 설정이 기준선과 다르다", page)

    def test_색인_규모가_다르면_경고(self):
        baseline = report({}, indexedLaws=500)
        page = rr.render_html(report(METRICS), baseline, 0.02, "x")
        self.assertIn("색인 법령·문항 수가 기준선과 다르다", page)

    def test_모르는_지표는_기타에_이스케이프해서(self):
        page = rr.render_html(report(METRICS), None, 0.02, "x")
        self.assertIn("기타", page)
        self.assertIn("new&lt;b&gt;.metric", page)
        self.assertNotIn("new<b>", page)
        self.assertNotIn("<script", page)

    def test_의도_차원_표(self):
        page = rr.render_html(report(METRICS), None, 0.02, "x")
        self.assertIn("변경점", page)
        self.assertIn("0.800", page)

    def test_의도_표에_기준선_변화가_셀_안에_보인다(self):
        baseline = report({"intent.DIFF.f1": {"value": 0.9, "n": 5}})
        page = rr.render_html(report(METRICS), baseline, 0.02, "x")
        self.assertIn('<br><span class="note">기준선 0.900', page)

    def test_비율과_점수_표기(self):
        page = rr.render_html(report(METRICS), None, 0.02, "x")
        self.assertIn("50.0% (7/14)", page)
        self.assertIn("0.750 (n=14)", page)


class MainTest(unittest.TestCase):

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp)

    def write(self, name, text, encoding="utf-8"):
        path = self.tmp / name
        path.write_text(text, encoding=encoding)
        return path

    def test_같은_폴더_같은_이름_HTML(self):
        src = self.write("2026-11-21-b6f522d.json", json.dumps(report(METRICS)))
        self.assertEqual(0, rr.main([str(src)]))
        self.assertTrue((self.tmp / "2026-11-21-b6f522d.html").is_file())

    def test_BOM_있는_파일도_읽는다(self):
        src = self.write("a.json", json.dumps(report(METRICS)), encoding="utf-8-sig")
        self.assertEqual(0, rr.main([str(src)]))

    def test_UTF16_파일도_읽는다(self):
        src = self.write("a.json", json.dumps(report(METRICS)), encoding="utf-16")
        self.assertEqual(0, rr.main([str(src)]))

    def test_기준선과_함께(self):
        src = self.write("a.json", json.dumps(report(METRICS)))
        base = self.write("base.json", json.dumps(report({"discovery.hit@3": {"value": 0.9, "n": 14}})))
        self.assertEqual(0, rr.main([str(src), "--baseline", str(base)]))
        page = (self.tmp / "a.html").read_text(encoding="utf-8")
        self.assertIn('class="verdict regressed"', page)

    def test_파일_없음(self):
        self.assertEqual(1, rr.main([str(self.tmp / "none.json")]))
        self.assertFalse((self.tmp / "none.html").exists())

    def test_JSON_아님(self):
        src = self.write("bad.json", "not json")
        self.assertEqual(1, rr.main([str(src)]))
        self.assertFalse((self.tmp / "bad.html").exists())

    def test_metrics_없음(self):
        src = self.write("nometrics.json", json.dumps({"config": {}}))
        self.assertEqual(1, rr.main([str(src)]))

    def test_지표_형식_오류(self):
        src = self.write("badmetric.json", json.dumps(report({"x": {"value": "high"}})))
        self.assertEqual(1, rr.main([str(src)]))

    def test_기준선_파일_없음(self):
        src = self.write("a.json", json.dumps(report(METRICS)))
        self.assertEqual(1, rr.main([str(src), "--baseline", str(self.tmp / "missing.json")]))
        self.assertFalse((self.tmp / "a.html").exists())


if __name__ == "__main__":
    unittest.main()
