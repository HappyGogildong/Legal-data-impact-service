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
trap 'rm -rf "${STATE:-}" "${REAL_DEPLOY:-}" "${DEPLOY:-}" "${ROOT_TEST:-}" "${LOCK_FILE:-}" "${FLOCK_LOG:-}" "${ORDER:-}"' EXIT
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

# --- main: 서버 2차 확인(auto 는 이미 배포된 것보다 옛 SHA 를 건너뜀). 임시 git 레포 + lia 대역 ---
REAL_DEPLOY="$DEPLOY"
ROOT="$(mktemp -d)"
ROOT_TEST="$ROOT"
git -C "$ROOT" init -q
git -C "$ROOT" config core.autocrlf false
git -C "$ROOT" config user.name test
git -C "$ROOT" config user.email test@example.invalid
git -C "$ROOT" commit -q --allow-empty -m A
COMMIT_A="$(git -C "$ROOT" rev-parse HEAD)"
git -C "$ROOT" commit -q --allow-empty -m B
COMMIT_B="$(git -C "$ROOT" rev-parse HEAD)"
git -C "$ROOT" branch -M main   # 서버 클론처럼 main 브랜치가 실제로 있다(.image-tag=main 사례)
DEPLOY="$(mktemp -d)"
ORDER="$(mktemp)"
LIA_LOG="$DEPLOY/lia.log"
cat > "$DEPLOY/lia" <<LIAEOF
#!/usr/bin/env bash
echo lia >> "$ORDER"
echo "\$*" >> "$LIA_LOG"
echo "\$2" > "$DEPLOY/.image-tag"
LIAEOF
chmod +x "$DEPLOY/lia"
printf '0 4 * * * run\n' > "$DEPLOY/crontab"
crontab() { printf '0 4 * * * run\n'; }
wait_for_jobs() { echo wait >> "$ORDER"; }
acquire_deploy_lock() { echo lock >> "$ORDER"; }
eval "orig_$(declare -f is_older_than_deployed)"
is_older_than_deployed() { echo check >> "$ORDER"; orig_is_older_than_deployed "$@"; }
reset_main() { : > "$LIA_LOG"; : > "$ORDER"; rm -f "$DEPLOY/.image-tag"; }
run_main() { ( main "$@" ) 2>&1; }

reset_main; echo "$COMMIT_B" > "$DEPLOY/.image-tag"
out="$(run_main "$COMMIT_A" auto)"; code=$?
check "auto: 옛 SHA 는 SKIPPED" grep -q "^SKIPPED tag=$COMMIT_A deployed=$COMMIT_B" <<<"$out"
check "auto: 옛 SHA 는 lia 미호출" test ! -s "$LIA_LOG"
check "auto: 옛 SHA 는 exit 0" test "$code" -eq 0

reset_main; echo "$COMMIT_A" > "$DEPLOY/.image-tag"
out="$(run_main "$COMMIT_B" auto)"
check "auto: 새 SHA 는 lia up" test "$(cat "$LIA_LOG")" = "up $COMMIT_B"
check "auto: 새 SHA 는 DEPLOYED" grep -q "^DEPLOYED tag=$COMMIT_B " <<<"$out"

reset_main; echo "$COMMIT_B" > "$DEPLOY/.image-tag"
out="$(run_main "$COMMIT_A" manual)"
check "manual: 롤백 허용" test "$(cat "$LIA_LOG")" = "up $COMMIT_A"

reset_main
run_main "$COMMIT_A" bogus > /dev/null; code=$?
check "모드 형식 오류는 64" test "$code" -eq 64
run_main abc auto > /dev/null; code=$?
check "태그 형식 오류는 64" test "$code" -eq 64

reset_main; echo garbage > "$DEPLOY/.image-tag"
run_main current auto > /dev/null; code=$?
check ".image-tag 가 SHA 가 아니면 실패" test "$code" -ne 0
check ".image-tag 가 SHA 가 아니면 lia 미호출" test ! -s "$LIA_LOG"

reset_main; echo "$COMMIT_B" > "$DEPLOY/.image-tag"
run_main "$COMMIT_A" > /dev/null
check "모드 기본값은 manual(롤백 허용)" test "$(cat "$LIA_LOG")" = "up $COMMIT_A"

reset_main; echo "$COMMIT_A" > "$DEPLOY/.image-tag"
run_main "$COMMIT_B" auto > /dev/null
check "순서: wait → lock → check → lia" test "$(tr '\n' ' ' < "$ORDER")" = "wait lock check lia "

reset_main; echo "$COMMIT_B" > "$DEPLOY/.image-tag"
run_main "$COMMIT_A" auto > /dev/null
check "순서(SKIPPED): wait → lock → check, lia 없음" test "$(tr '\n' ' ' < "$ORDER")" = "wait lock check "

reset_main; echo main > "$DEPLOY/.image-tag"
out="$(run_main "$COMMIT_A" auto)"; code=$?
check ".image-tag=main + auto 는 실패" test "$code" -ne 0
check ".image-tag=main + auto 는 lia 미호출" test ! -s "$LIA_LOG"
check ".image-tag=main + auto 는 SKIPPED 아님" not grep -q '^SKIPPED' <<<"$out"

reset_main; echo "$COMMIT_A" > "$DEPLOY/.image-tag"
out="$(run_main "$COMMIT_A" auto)"
check "auto: 같은 SHA 는 DEPLOYED" grep -q "^DEPLOYED tag=$COMMIT_A " <<<"$out"
check "auto: 같은 SHA 는 lia up" test "$(cat "$LIA_LOG")" = "up $COMMIT_A"

reset_main
out="$(run_main "$COMMIT_A" auto)"
check "auto: .image-tag 없으면 DEPLOYED" grep -q "^DEPLOYED tag=$COMMIT_A " <<<"$out"

reset_main; echo "$COMMIT_B" > "$DEPLOY/.image-tag"
out="$(run_main current auto)"
check "auto current 는 건너뛰지 않는다" test "$(cat "$LIA_LOG")" = "up $COMMIT_B"

reset_main
unknown=0123456789abcdef0123456789abcdef01234567
echo "$COMMIT_B" > "$DEPLOY/.image-tag"
run_main "$unknown" auto > /dev/null; code=$?
check "커밋이 클론에 없으면 실패(fail-closed)" test "$code" -ne 0
check "커밋이 클론에 없으면 lia 미호출" test ! -s "$LIA_LOG"

echo "실패 $fails"
(( fails == 0 ))
