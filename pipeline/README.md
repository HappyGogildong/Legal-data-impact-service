# pipeline — 수집·소스분석·해석 (Python)

신뢰 출처에서 법안을 가져와 표준 `Bill`로 정규화하고, 사용자 입력(링크/영상)을
법안으로 식별하며, LLM으로 영향을 추론한다.

```
src/lia_pipeline/
├── models.py              표준 도메인 모델 (RawBill, Bill, Article ...)
├── connectors/            출처별 어댑터 (SourceConnector 구현)
│   ├── base.py
│   ├── assembly_bills.py  열린국회정보 OpenAPI
│   └── moleg_notice.py    법제처 정부입법예고
├── ingest/
│   ├── source_analyzer.py 링크/영상/텍스트 → 법안 식별
│   └── normalizer.py      RawBill → Bill
├── analysis/
│   └── engine.py          RAG + LLM 영향 추론 + 인용 검증
└── demo.py                수직 슬라이스 데모
```

새 출처를 붙이려면 `connectors/`에 `SourceConnector` 구현체를 하나 추가하면 된다.
