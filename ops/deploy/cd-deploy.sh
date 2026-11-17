#!/usr/bin/env bash
# LIA CD 서버 단계(D86) — SSM 문서 LIA-Deploy 가 git pull 뒤 실행한다. 손으로 실행해도 된다.
#   sudo bash ops/deploy/cd-deploy.sh <커밋 SHA 40자 | current> [auto|manual]
# 태그·모드 검사 → 배치·평가가 끝나길 대기(최대 60분) → 배포 잠금(최대 10분, 다른 배포와 직렬화)
#   → 태그 해석 → (auto 면) 이미 배포된 것보다 옛 SHA 인지 확인 → lia up → crontab 차이 경고 → 'DEPLOYED …' 한 줄.
# 잠금은 배치 대기 뒤에 잡는다(기다리는 동안 다른 배포의 git pull 을 막지 않게). current 는 잠금 뒤에 해석한다.
# flock 은 FIFO 가 아니라 배치 대기 중 순서가 뒤바뀔 수 있다 — 그래서 잠금을 잡은 뒤 서버에서 한 번 더 확인한다.
#   auto(자동 배포): 요청 SHA 가 배포본(.image-tag)의 조상이면 'SKIPPED …' 를 내고 0 으로 끝낸다.
#   manual(기본값 — 사람이 손으로 실행하면 그 SHA 를 의도한 것으로 본다): 옛 SHA 도 배포한다(롤백).
# 종료 코드: 0 성공·건너뜀 · 64 태그·모드 형식 오류 · 1 그 밖의 실패.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY="$ROOT/ops/deploy"
WAIT_MAX_SECONDS="${CD_WAIT_MAX_SECONDS:-3600}"
WAIT_INTERVAL_SECONDS="${CD_WAIT_INTERVAL_SECONDS:-30}"
LOCK_FILE="${CD_LOCK_FILE:-/var/lock/lia-deploy.lock}"
LOCK_WAIT_SECONDS="${CD_LOCK_WAIT_SECONDS:-600}"

valid_tag() { [[ "$1" =~ ^([0-9a-f]{40}|current)$ ]]; }
valid_mode() { [[ "$1" =~ ^(auto|manual)$ ]]; }

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

# 동시에 온 배포를 직렬화한다(GitHub concurrency 는 대기 실행을 하나만 남기고 취소해서 서버에서 잠근다).
# 잠금은 이 프로세스가 끝날 때 풀린다(fd 9)
acquire_deploy_lock() {
  exec 9>"$LOCK_FILE"
  flock -w "$LOCK_WAIT_SECONDS" 9 || { echo "다른 배포가 $((LOCK_WAIT_SECONDS / 60))분 넘게 진행 중 — 배포 중단" >&2; return 1; }
}

# current → 마지막으로 배포한 태그(.image-tag). 40자 hex 가 아니면 실패한다
resolve_tag() {
  if [[ "$1" == current ]]; then
    [[ -s "$DEPLOY/.image-tag" ]] || { echo "현재 태그(.image-tag)가 없다 — 먼저 SHA 로 배포" >&2; return 1; }
    local deployed
    deployed="$(cat "$DEPLOY/.image-tag")"
    [[ "$deployed" =~ ^[0-9a-f]{40}$ ]] || { echo ".image-tag 내용이 커밋 SHA 40자가 아니다: '$deployed'" >&2; return 1; }
    echo "$deployed"
  else
    echo "$1"
  fi
}

# 0 = 요청 SHA 가 배포본의 조상(옛것) · 1 = 아님(배포본 없음·같은 SHA 포함) · 2 = 판단 불가(호출부가 실패시킨다)
is_older_than_deployed() {
  local tag="$1" deployed rc=0
  [[ -s "$DEPLOY/.image-tag" ]] || return 1
  deployed="$(cat "$DEPLOY/.image-tag")"
  [[ "$tag" != "$deployed" ]] || return 1
  git -C "$ROOT" merge-base --is-ancestor "$tag" "$deployed" 2>/dev/null || rc=$?
  case "$rc" in
    0) return 0 ;;
    1) return 1 ;;
    *) echo "커밋 선후를 알 수 없다(클론에 없는 커밋?): $tag vs $deployed" >&2; return 2 ;;
  esac
}

# crontab 은 유료 배치 일정이라 자동 설치하지 않고 알리기만 한다
warn_crontab_drift() {
  if ! diff -q <(crontab -l 2>/dev/null) "$DEPLOY/crontab" > /dev/null; then
    echo "CRONTAB_DRIFT 레포 crontab 이 설치본과 다르다 — 사람이 'sudo crontab $DEPLOY/crontab'"
  fi
}

main() {
  local requested="${1:-}" mode="${2:-manual}" tag rc
  valid_tag "$requested" || { echo "태그 형식 오류: '$requested' (커밋 SHA 40자 또는 current)" >&2; exit 64; }
  valid_mode "$mode" || { echo "모드 형식 오류: '$mode' (auto 또는 manual)" >&2; exit 64; }
  wait_for_jobs || exit 1
  acquire_deploy_lock || exit 1
  tag="$(resolve_tag "$requested")" || exit 1
  if [[ "$mode" == auto && "$requested" != current ]]; then
    rc=0
    is_older_than_deployed "$tag" || rc=$?
    case "$rc" in
      0) echo "SKIPPED tag=$tag deployed=$(cat "$DEPLOY/.image-tag") — 이미 더 새 이미지가 배포됨"; exit 0 ;;
      1) ;;
      *) exit 1 ;;
    esac
  fi
  "$DEPLOY/lia" up "$tag" || exit 1
  warn_crontab_drift
  echo "DEPLOYED tag=$tag commit=$(git -C "$ROOT" rev-parse --short HEAD)"
}

[[ "${CD_DEPLOY_SOURCE_ONLY:-}" == 1 ]] || main "$@"
