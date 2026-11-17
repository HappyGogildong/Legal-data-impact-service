#!/usr/bin/env bash
# deploy.yml 의 태그 결정(D86) — 표준 출력 한 줄: 커밋 SHA 40자 · current · skip.
#   resolve-tag.sh <input_tag> <before_sha> <sha>
# input_tag 가 있으면 workflow_call·workflow_dispatch(그 값을 검사해 그대로).
# 없으면 push: 푸시 범위에 ci-core 트리거 경로가 있으면 skip(ci-core 가 새 이미지로 배포한다), 아니면 current.
set -euo pipefail

input="${1:-}"; before="${2:-}"; sha="${3:-}"
# ci-core.yml 의 on.push.paths 와 같게 유지한다
CI_CORE_PATHS='^(core/|api/|web/src/lib/profile-options\.json$|\.github/workflows/ci-core\.yml$)'

if [[ -n "$input" ]]; then
  tag="$input"
else
  tag=current
  if [[ -n "$before" && ! "$before" =~ ^0+$ ]]; then
    changed="$(git diff --name-only "$before" "$sha")"   # 파이프로 grep -q 하면 SIGPIPE + pipefail 로 오판한다
    if grep -qE "$CI_CORE_PATHS" <<<"$changed"; then
      echo skip
      exit 0
    fi
  fi
fi

[[ "$tag" =~ ^([0-9a-f]{40}|current)$ ]] || { echo "태그 형식 오류: '$tag' (커밋 SHA 40자 또는 current)" >&2; exit 64; }
echo "$tag"
