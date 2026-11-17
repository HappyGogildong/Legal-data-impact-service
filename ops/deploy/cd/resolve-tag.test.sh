#!/usr/bin/env bash
# resolve-tag.sh 시험(D86) — 임시 git 레포에서 푸시 범위를 흉내 낸다.
#   bash ops/deploy/cd/resolve-tag.test.sh
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

SHA=0123456789abcdef0123456789abcdef01234567
REPO="$(mktemp -d)"; cd "$REPO"
git init -q && git config user.email test@example.invalid && git config user.name test
commit() { mkdir -p "$(dirname "$1")"; echo "$RANDOM" >> "$1"; git add -A; git commit -qm "$1"; git rev-parse HEAD; }
base="$(commit README.md)"
ops="$(commit ops/deploy/docker-compose.prod.yml)"
core="$(commit core/src/Main.java)"
api="$(commit api/openapi.json)"
wf="$(commit .github/workflows/ci-core.yml)"
profile="$(commit web/src/lib/profile-options.json)"
dash="$(commit ops/observability/grafana/x.json)"

# 입력 태그(workflow_call·workflow_dispatch)
expect "입력 SHA 그대로" "$SHA" 0 -- "$SHA" "" ""
expect "입력 current 그대로" current 0 -- current "" ""
expect "입력 형식 오류" "" 64 -- abc "" ""
expect "입력 대문자 거부" "" 64 -- "${SHA^^}" "" ""

# push — ops 만 바뀜
expect "ops 만 → current" current 0 -- "" "$base" "$ops"
expect "대시보드만 → current" current 0 -- "" "$profile" "$dash"
# push — ci-core 경로가 범위에 있으면 skip
expect "core 포함 → skip" skip 0 -- "" "$base" "$core"
expect "api 포함 → skip" skip 0 -- "" "$core" "$api"
expect "ci-core.yml 포함 → skip" skip 0 -- "" "$api" "$wf"
expect "프로필 선택지 포함 → skip" skip 0 -- "" "$wf" "$profile"
expect "여러 커밋 중 core 하나 → skip" skip 0 -- "" "$base" "$dash"
# push — before 가 비었거나 0 SHA(브랜치 첫 푸시) → current
expect "before 없음 → current" current 0 -- "" "" "$ops"
expect "before 0 SHA → current" current 0 -- "" 0000000000000000000000000000000000000000 "$ops"

cd /; rm -rf "$REPO"
echo "실패 $fails"
(( fails == 0 ))
