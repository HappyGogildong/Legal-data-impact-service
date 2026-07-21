"""설정 로딩 — YAML 파일에서 주입.

API 키·서비스명 등은 코드/환경변수에 흩지 않고 `config.yaml` 한 곳에서 관리한다.
- 값에 ``${ENV_VAR}`` / ``${ENV_VAR:-default}`` 보간 지원(환경변수·.env).
- 보간 없이 키 문자열을 직접 넣어도 된다.
- 실제 키가 든 `config.yaml`은 .gitignore 대상. 커밋용은 `config.example.yaml`.

탐색 순서: 인자 path → `LIA_CONFIG` 환경변수 → pipeline/config.yaml → pipeline/config.local.yaml.
파일이 없으면 기본값(빈 키)으로 동작(오프라인/데모).
"""
from __future__ import annotations

import os
import re
from functools import lru_cache
from pathlib import Path

import yaml
from pydantic import BaseModel

# best-effort: 레포 루트 .env 를 환경변수로 로드(있을 때만)
try:  # pragma: no cover
    from pathlib import Path as _Path

    from dotenv import load_dotenv

    # config.py: pipeline/src/lia_pipeline/config.py → parents[3] == 레포 루트
    _root_env = _Path(__file__).resolve().parents[3] / ".env"
    load_dotenv(_root_env if _root_env.exists() else None)
except Exception:  # python-dotenv 미설치 등
    pass

_ENV = re.compile(r"\$\{([A-Za-z0-9_]+)(?::-([^}]*))?\}")


def _interpolate(value):
    """문자열 내 ${VAR} / ${VAR:-default} 를 환경변수로 치환(재귀)."""
    if isinstance(value, str):
        return _ENV.sub(lambda m: os.environ.get(m.group(1), m.group(2) or ""), value)
    if isinstance(value, dict):
        return {k: _interpolate(v) for k, v in value.items()}
    if isinstance(value, list):
        return [_interpolate(v) for v in value]
    return value


# --- 설정 스키마 -------------------------------------------------------
class AssemblySettings(BaseModel):
    api_key: str = ""
    service: str = "nzmimeepazxkubdpn"   # 의원발의 법률안 목록
    age: str = "22"                      # 국회 대수(필수). 22대=현재
    base: str = "https://open.assembly.go.kr/portal/openapi"
    page_size: int = 100
    timeout: float = 20.0
    max_retries: int = 3


class MolegSettings(BaseModel):
    """법제처 정부입법예고 — 정부제출 '법안'(입법예고). ≠ 국가법령정보.

    인증: ServiceKey가 아니라 **OC(회원 이메일 아이디)**. (lawmaking.go.kr 계열)
    """
    oc: str = ""
    base: str = "https://opinion.lawmaking.go.kr"


class LawSettings(BaseModel):
    """국가법령정보(open.law.go.kr) — '현행 법령'(diff 기준선). ≠ 입법예고.

    인증: **OC(회원 이메일 아이디)**. 요청 파라미터명은 보통 ``OC``.
    """
    oc: str = ""
    base: str = "https://www.law.go.kr"


class SourcesSettings(BaseModel):
    # 내용 기준: 법제처(MOLEG)가 입법예고·국가법령정보를 둘 다 운영하니 주의.
    assembly: AssemblySettings = AssemblySettings()  # 열린국회 — 의원발의 법안
    moleg: MolegSettings = MolegSettings()           # 법제처 입법예고 — 정부제출 법안
    law: LawSettings = LawSettings()                 # 국가법령정보 — 현행 법령


class EmbeddingSettings(BaseModel):
    provider: str = "openai"        # openai | upstage
    model: str = "text-embedding-3-small"
    dim: int = 1536
    api_key: str = ""


class LLMSettings(BaseModel):
    provider: str = "anthropic"
    model: str = "claude-opus-4-8"
    api_key: str = ""


class DatabaseSettings(BaseModel):
    url: str = ""


class Settings(BaseModel):
    sources: SourcesSettings = SourcesSettings()
    embedding: EmbeddingSettings = EmbeddingSettings()
    llm: LLMSettings = LLMSettings()
    database: DatabaseSettings = DatabaseSettings()


_DEFAULT_NAMES = ("config.yaml", "config.local.yaml")


def _pipeline_dir() -> Path:
    # .../pipeline/src/lia_pipeline/config.py → parents[2] == pipeline/
    return Path(__file__).resolve().parents[2]


def _resolve_path(path: str | os.PathLike | None) -> Path | None:
    if path:
        return Path(path)
    if os.environ.get("LIA_CONFIG"):
        return Path(os.environ["LIA_CONFIG"])
    for base in (_pipeline_dir(), Path.cwd()):
        for name in _DEFAULT_NAMES:
            cand = base / name
            if cand.exists():
                return cand
    return None


def load_settings(path: str | os.PathLike | None = None) -> Settings:
    """YAML 설정 로드(+ env 보간). 파일 없으면 기본값."""
    p = _resolve_path(path)
    data: dict = {}
    if p and p.exists():
        raw = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
        data = _interpolate(raw)
    return Settings.model_validate(data)


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """앱 전역 설정(캐시). 테스트는 load_settings(path)로 비캐시 로드."""
    return load_settings()


# --- 팩토리 (설정 → 컴포넌트) -----------------------------------------
def build_assembly_connector(settings: Settings | None = None):
    """설정으로부터 AssemblyBillsConnector 생성."""
    from .connectors.assembly_bills import AssemblyBillsConnector

    s = (settings or get_settings()).sources.assembly
    return AssemblyBillsConnector(
        api_key=s.api_key,
        service=s.service,
        age=s.age,
        base=s.base,
        page_size=s.page_size,
        timeout=s.timeout,
        max_retries=s.max_retries,
    )
