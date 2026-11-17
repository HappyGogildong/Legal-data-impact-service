#!/usr/bin/env bash
# LIA CD 서버 단계(D86) — SSM 문서 LIA-Deploy 가 git pull 뒤 실행한다. 손으로 실행해도 된다.
#   sudo bash ops/deploy/cd-deploy.sh <커밋 SHA 40자 | current>
# 태그 검사 → 배치·평가가 끝나길 대기(최대 60분) → lia up → crontab 차이 경고 → 'DEPLOYED …' 한 줄.
# 종료 코드: 0 성공 · 64 태그 형식 오류 · 1 그 밖의 실패.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY="$ROOT/ops/deploy"
WAIT_MAX_SECONDS="${CD_WAIT_MAX_SECONDS:-3600}"
WAIT_INTERVAL_SECONDS="${CD_WAIT_INTERVAL_SECONDS:-30}"

valid_tag() { [[ "$1" =~ ^([0-9a-f]{40}|current)$ ]]; }

# 실행 중인 배치(ingest)·평가(eval) 컨테이너 수
running_jobs() {
  { docker ps --filter name=ingest --filter status=running -q
    docker ps --filter name=eval --filter status=running -q; } | grep -c . || true
}

# 배치 도중 새 core 가 Flyway 마이그레이션을 하지 않도록 끝나길 기다린다
wait_for_jobs() {
  local waited=0
  while (( $(running_jobs) > 0 )); do
    if (( waited >= WAIT_MAX_SECONDS )); then
      echo "배치·평가가 $((WAIT_MAX_SECONDS / 60))분 넘게 실행 중 — 배포 중단. 끝난 뒤 수동 실행" >&2
      return 1
    fi
    echo "배치 실행 중 — 대기 $((waited / 60))분"
    sleep "$WAIT_INTERVAL_SECONDS"
    waited=$((waited + WAIT_INTERVAL_SECONDS))
  done
}

# current → 마지막으로 배포한 태그(.image-tag)
resolve_tag() {
  if [[ "$1" == current ]]; then
    [[ -s "$DEPLOY/.image-tag" ]] || { echo "현재 태그(.image-tag)가 없다 — 먼저 SHA 로 배포" >&2; return 1; }
    cat "$DEPLOY/.image-tag"
  else
    echo "$1"
  fi
}

# crontab 은 유료 배치 일정이라 자동 설치하지 않고 알리기만 한다
warn_crontab_drift() {
  if ! diff -q <(crontab -l 2>/dev/null) "$DEPLOY/crontab" > /dev/null; then
    echo "CRONTAB_DRIFT 레포 crontab 이 설치본과 다르다 — 사람이 'sudo crontab $DEPLOY/crontab'"
  fi
}

main() {
  local requested="${1:-}" tag
  valid_tag "$requested" || { echo "태그 형식 오류: '$requested' (커밋 SHA 40자 또는 current)" >&2; exit 64; }
  wait_for_jobs
  tag="$(resolve_tag "$requested")"
  "$DEPLOY/lia" up "$tag"
  warn_crontab_drift
  echo "DEPLOYED tag=$tag commit=$(git -C "$ROOT" rev-parse --short HEAD)"
}

[[ "${CD_DEPLOY_SOURCE_ONLY:-}" == 1 ]] || main "$@"
