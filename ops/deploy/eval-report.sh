#!/usr/bin/env bash
# lia eval 보고서 사본(D83) — 컨테이너 출력에서 보고서 한 줄('{"config"' 로 시작)을 골라 서버에 JSON 으로 남긴다.
#   eval-report.sh <출력 파일> <이미지 태그> <저장 폴더>
# 이름: YYYY-MM-DD-<태그 앞 7자>.json (KST). 같은 이름이 있으면 -HHMMSS(KST)를 붙인다. 쓴 경로는 표준 오류로.
# 보고서 줄이 없으면(실행 실패) 아무것도 쓰지 않고 0 — 실패는 실행기의 종료 코드가 알린다.
set -euo pipefail

usage="사용법: eval-report.sh <출력 파일> <이미지 태그> <저장 폴더>"
out_file="${1:?$usage}"
tag="${2-}"
dir="${3:?$usage}"

if [[ ! "$tag" =~ ^[0-9a-f]{7,}$ ]]; then
  echo "eval-report: 이미지 태그가 16진 7자 이상이 아니다: '$tag'" >&2
  exit 64
fi

line="$(grep -m1 '^{"config"' "$out_file" || true)"
if [[ -z "$line" ]]; then
  echo "eval-report: 보고서 줄이 없다 — 사본을 남기지 않는다" >&2
  exit 0
fi

mkdir -p "$dir"
name="$(TZ=Asia/Seoul date +%F)-${tag:0:7}"
path="$dir/$name.json"
if [[ -e "$path" ]]; then
  path="$dir/$name-$(TZ=Asia/Seoul date +%H%M%S).json"
fi
printf '%s\n' "$line" > "$path"
echo "eval-report: 보고서 사본 $path" >&2
