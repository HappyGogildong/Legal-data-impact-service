#!/usr/bin/env bash
# cd-deploy.sh 함수 단위 시험(D86) — 로컬(Git Bash·Linux): bash ops/deploy/cd/cd-deploy.test.sh
# docker·crontab·sleep 을 셸 함수로 바꿔 끼워 서버 없이 돈다.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CD_DEPLOY_SOURCE_ONLY=1 source "$HERE/../cd-deploy.sh"
set +e

fails=0
check() { local name="$1"; shift; if "$@"; then echo "ok   $name"; else echo "FAIL $name"; fails=$((fails + 1)); fi; }
not() { ! "$@"; }

SHA=0123456789abcdef0123456789abcdef01234567

# --- 태그 형식 ---
check "40자 SHA 허용" valid_tag "$SHA"
check "current 허용" valid_tag current
check "짧은 SHA 거부" not valid_tag 0123456
check "41자 거부" not valid_tag "${SHA}8"
check "대문자 거부" not valid_tag 0123456789ABCDEF0123456789abcdef01234567
check "명령 끼워 넣기 거부" not valid_tag "current; rm -rf /"
check "줄바꿈 거부" not valid_tag $'current\nrm'
check "빈 값 거부" not valid_tag ""

# --- 배치·평가 대기: docker 를 함수로 ---
STATE="$(mktemp)"            # "<컨테이너 이름 조각> <남은 실행 중 조회 횟수>"
docker() {
  local job left
  read -r job left < "$STATE"
  if [[ "$*" == *"name=$job"* ]] && (( left > 0 )); then
    echo deadbeef
    echo "$job $((left - 1))" > "$STATE"
  fi
}
sleep() { :; }
WAIT_INTERVAL_SECONDS=30

echo "ingest 0" > "$STATE"; WAIT_MAX_SECONDS=3600
check "도는 작업이 없으면 바로 진행" wait_for_jobs
echo "ingest 2" > "$STATE"
check "배치가 끝나면 진행" wait_for_jobs
echo "eval 2" > "$STATE"
check "평가도 기다린다" wait_for_jobs
echo "ingest 999" > "$STATE"; WAIT_MAX_SECONDS=60
check "상한을 넘으면 실패" not wait_for_jobs

# --- 태그 해석 ---
DEPLOY="$(mktemp -d)"
check "SHA 는 그대로" test "$(resolve_tag "$SHA")" = "$SHA"
check ".image-tag 없으면 current 실패" not resolve_tag current
echo "$SHA" > "$DEPLOY/.image-tag"
check "current 는 .image-tag" test "$(resolve_tag current)" = "$SHA"

# --- crontab 차이 경고 ---
printf '0 4 * * * run\n' > "$DEPLOY/crontab"
crontab() { printf '0 4 * * * run\n'; }
check "같으면 경고 없음" test -z "$(warn_crontab_drift)"
crontab() { printf '0 13 * * * run\n'; }
check "다르면 CRONTAB_DRIFT" grep -q '^CRONTAB_DRIFT' <<<"$(warn_crontab_drift)"

# --- 배포 잠금: flock 을 함수로(Git Bash 에는 없다). 잠금 함수가 exec 9> 를 쓰므로 서브셸에서 시험 ---
LOCK_FILE="$(mktemp)"
FLOCK_LOG="$(mktemp)"
FLOCK_RESULT=0
flock() { echo "$*" >> "$FLOCK_LOG"; return "$FLOCK_RESULT"; }
LOCK_WAIT_SECONDS=600
FLOCK_RESULT=0; : > "$FLOCK_LOG"
( acquire_deploy_lock ) ; check "flock 성공 시 성공" test $? -eq 0
check "flock 을 -w 600 9 로 부른다" test "$(cat "$FLOCK_LOG")" = "-w 600 9"
FLOCK_RESULT=1
( acquire_deploy_lock ) 2>/dev/null; check "flock 실패 시 실패" test $? -ne 0
check "잠금 실패 메시지" grep -q '10분 넘게' <<<"$( ( acquire_deploy_lock ) 2>&1 )"

rm -rf "$STATE" "$DEPLOY" "$LOCK_FILE" "$FLOCK_LOG"
echo "실패 $fails"
(( fails == 0 ))
