#!/usr/bin/env bash
# resolve-tag.sh 시험(D86) — 임시 git 레포에서 푸시 범위를 흉내 낸다.
#   bash ops/deploy/cd/resolve-tag.test.sh
# 인자 순서: <event_name> <input_tag> <before_sha> <sha>, 비교 기준은 환경변수 MAIN_REF
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESOLVE="$HERE/resolve-tag.sh"

fails=0
expect() {  # expect <이름> <기대 출력> <기대 종료 코드> -- <인자…>
  local name="$1" want="$2" want_code="$3"; shift 4
  local got code
  got="$(bash "$RESOLVE" "$@" 2>/dev/null)"; code=$?
  if [[ "$got" == "$want" && "$code" == "$want_code" ]]; then echo "ok   $name"
  else echo "FAIL $name — 출력 '$got' 종료 $code (기대 '$want' $want_code)"; fails=$((fails + 1)); fi
}

expect_stderr() {  # expect_stderr <이름> <stderr 에 있어야 할 문구> -- <인자…>
  local name="$1" want="$2"; shift 3
  local err
  err="$(bash "$RESOLVE" "$@" 2>&1 >/dev/null)"
  if [[ "$err" == *"$want"* ]]; then echo "ok   $name"
  else echo "FAIL $name — stderr '$err' 에 '$want' 없음"; fails=$((fails + 1)); fi
}

SHA=0123456789abcdef0123456789abcdef01234567
REPO="$(mktemp -d)"; trap 'rm -rf "$REPO"' EXIT
cd "$REPO"
git init -q && git config user.email test@example.invalid && git config user.name test && git config core.autocrlf false
commit() { mkdir -p "$(dirname "$1")"; echo "$RANDOM" >> "$1"; git add -A; git commit -qm "$1"; git rev-parse HEAD; }
base="$(commit README.md)"
ops="$(commit ops/deploy/docker-compose.prod.yml)"
core="$(commit core/src/Main.java)"
api="$(commit api/openapi.json)"
wf="$(commit .github/workflows/ci-core.yml)"
profile="$(commit web/src/lib/profile-options.json)"
dash="$(commit ops/observability/grafana/x.json)"
mkdir -p docs; git mv core/src/Main.java docs/Main.java; git commit -qm "move out of core"; renamed="$(git rev-parse HEAD)"
MISSING=deadbeefdeadbeefdeadbeefdeadbeefdeadbeef
export MAIN_REF="$renamed"

# 수동 실행(workflow_dispatch) — 입력을 검사해 그대로(롤백이라 옛 SHA 허용)
expect "수동 SHA 그대로" "$SHA" 0 -- workflow_dispatch "$SHA" "" ""
expect "수동 current 그대로" current 0 -- workflow_dispatch current "" ""
expect "수동 형식 오류" "" 64 -- workflow_dispatch abc "" ""
expect "수동 대문자 거부" "" 64 -- workflow_dispatch "${SHA^^}" "" ""
expect_stderr "형식 오류 메시지" "태그 형식 오류" -- workflow_dispatch abc "" ""
MAIN_REF="$dash" expect "(d) 수동 + 옛 SHA → 롤백 허용" "$core" 0 -- workflow_dispatch "$core" "" "$dash"

# 입력 태그가 있는 push(= ci-core 의 workflow_call) — 그 뒤 main 에 core 변경이 있으면 skip
MAIN_REF="$profile" expect "(a) 최신 core 커밋 → 그 SHA" "$profile" 0 -- push "$profile" "" ""
MAIN_REF="$dash" expect "(b) 옛 core 커밋, 뒤에 core 커밋 → skip" skip 0 -- push "$core" "" ""
MAIN_REF="$dash" expect "(c) core 커밋, 뒤엔 ops 만 → 그 SHA" "$profile" 0 -- push "$profile" "" ""
MAIN_REF="$renamed" expect "(f) 뒤에 core 밖으로 옮긴 커밋 → skip" skip 0 -- push "$dash" "" ""
expect "호출 current → 그대로" current 0 -- push current "" ""
expect "호출 형식 오류" "" 64 -- push abc "" ""
expect_stderr "호출 형식 오류 메시지" "태그 형식 오류" -- push abc "" ""
expect_stderr "건너뜀 이유를 stderr 에" "건너" -- push "$core" "" ""

# push — ops 만 바뀜
expect "ops 만 → current" current 0 -- push "" "$base" "$ops"
expect "대시보드만 → current" current 0 -- push "" "$profile" "$dash"
# push — ci-core 경로가 범위에 있으면 skip
expect "core 포함 → skip" skip 0 -- push "" "$base" "$core"
expect "api 포함 → skip" skip 0 -- push "" "$core" "$api"
expect "ci-core.yml 포함 → skip" skip 0 -- push "" "$api" "$wf"
expect "프로필 선택지 포함 → skip" skip 0 -- push "" "$wf" "$profile"
expect "여러 커밋 중 core 하나 → skip" skip 0 -- push "" "$base" "$dash"
expect "core 밖으로 옮김(rename) → skip" skip 0 -- push "" "$dash" "$renamed"
# push — before 가 비었거나 0 SHA(브랜치 첫 푸시) 또는 이 체크아웃에 없음(force-push) → current
expect "before 없음 → current" current 0 -- push "" "" "$ops"
expect "before 0 SHA → current" current 0 -- push "" 0000000000000000000000000000000000000000 "$ops"
expect "(e) before 가 체크아웃에 없음 → current" current 0 -- push "" "$MISSING" "$ops"
expect_stderr "(e) 없는 before 를 알린다" "before" -- push "" "$MISSING" "$ops"

cd /
echo "실패 $fails"
(( fails == 0 ))
