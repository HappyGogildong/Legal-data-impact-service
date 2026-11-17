#!/usr/bin/env bash
# deploy.yml 의 태그 결정(D86) — 표준 출력 한 줄: 커밋 SHA 40자 · current · skip.
#   resolve-tag.sh <event_name> <input_tag> <before_sha> <sha>
# event_name 은 github.event_name — workflow_call 로 불려도 호출자 이벤트(push)다.
#   workflow_dispatch + input_tag : 수동 실행(롤백 포함) — 형식을 검사해 그대로(옛 SHA 허용).
#   그 밖 + input_tag             : ci-core 가 부른 SHA — 그 뒤 main(MAIN_REF, 기본 origin/main)에
#                                   ci-core 경로 변경이 있으면 skip(더 새 이미지가 따로 배포한다), 없으면 그 SHA.
#   input_tag 없음(push)          : 푸시 범위에 ci-core 경로가 있으면 skip(ci-core 가 새 이미지로 배포), 아니면 current.
set -euo pipefail

event="${1:-}"; input="${2:-}"; before="${3:-}"; sha="${4:-}"
MAIN_REF="${MAIN_REF:-origin/main}"
# ci-core.yml 의 on.push.paths 와 같게 유지한다
CI_CORE_PATHS='^(core/|api/|web/src/lib/profile-options\.json$|\.github/workflows/ci-core\.yml$)'

# 변경 파일 목록(CHANGED)에 ci-core 경로가 있나 — 파이프로 grep -q 하면 SIGPIPE + pipefail 로 오판해 변수에 담는다
# git 오류(없는 ref 등)가 if 조건 안에서 묻히지 않게 diff 는 조건 밖에서 돌린다
load_changed() {
  CHANGED="$(git diff --name-only --no-renames "$1" "$2")"
}
changed_has_core() { grep -qE "$CI_CORE_PATHS" <<<"$CHANGED"; }

if [[ -n "$input" ]]; then
  tag="$input"
  if [[ "$event" != workflow_dispatch && "$tag" =~ ^([0-9a-f]{40}|current)$ && "$tag" != current ]]; then
    load_changed "$tag" "$MAIN_REF"
    if changed_has_core; then
      echo "$tag 뒤 $MAIN_REF 에 ci-core 경로 변경이 있다 — 더 새 이미지가 배포하므로 건너뜀" >&2
      echo skip
      exit 0
    fi
  fi
else
  tag=current
  if [[ -n "$before" && ! "$before" =~ ^0+$ ]]; then
    if ! git cat-file -e "$before^{commit}" 2>/dev/null; then
      echo "before $before 가 이 체크아웃에 없다(force-push 등) — current 로 본다" >&2
    else
      load_changed "$before" "$sha"
      if changed_has_core; then
        echo skip
        exit 0
      fi
    fi
  fi
fi

[[ "$tag" =~ ^([0-9a-f]{40}|current)$ ]] || { echo "태그 형식 오류: '$tag' (커밋 SHA 40자 또는 current)" >&2; exit 64; }
echo "$tag"
