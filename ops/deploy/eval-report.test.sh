#!/usr/bin/env bash
# eval-report.sh 시험(D83) — 임시 폴더에서 보고서 사본 규칙을 확인한다.
#   bash ops/deploy/eval-report.test.sh
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SAVE="$HERE/eval-report.sh"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

fails=0
ok() { echo "ok   $1"; }
fail() { echo "FAIL $1 — $2"; fails=$((fails + 1)); }

TAG=0123456789abcdef0123456789abcdef01234567
NAME="$(TZ=Asia/Seoul date +%F)-0123456"
REPORT='{"config":{"chunkFetch":40,"maxCandidates":10,"minSimilarity":0.3},"indexedLaws":1,"cases":1,"excludedCases":0,"metrics":{}}'

OUT="$TMP/out.txt"
printf '%s\n' \
  '2026-11-21T00:00:00.000Z  INFO 1 --- [lia-core] [main] c.l.c.eval.OfflineEvalRunner : 시작' \
  "$REPORT" \
  '2026-11-21T00:00:01.000Z  INFO 1 --- [lia-core] [main] c.l.c.eval.OfflineEvalRunner : [eval] 기준선 없음' > "$OUT"

# 1. Spring 로그 사이의 보고서 줄만 골라 저장
dir="$TMP/save1"
bash "$SAVE" "$OUT" "$TAG" "$dir" 2>/dev/null; code=$?
if [[ $code == 0 && "$(cat "$dir/$NAME.json" 2>/dev/null)" == "$REPORT" ]]; then ok "보고서 줄 저장"
else fail "보고서 줄 저장" "종료 $code, 폴더 '$(ls "$dir" 2>/dev/null)'"; fi

# 2. 저장 경로를 표준 오류로 알림
err="$(bash "$SAVE" "$OUT" "$TAG" "$TMP/save2" 2>&1 >/dev/null)"
if [[ "$err" == *"보고서 사본 $TMP/save2/$NAME.json"* ]]; then ok "경로 알림"
else fail "경로 알림" "stderr '$err'"; fi

# 3. 보고서 줄이 없으면(실행 실패) 아무것도 쓰지 않고 0
NOREPORT="$TMP/noreport.txt"; printf '%s\n' 'ERROR [eval] 평가 실패 — 보고서를 내지 않는다' > "$NOREPORT"
bash "$SAVE" "$NOREPORT" "$TAG" "$TMP/save3" 2>/dev/null; code=$?
if [[ $code == 0 && ! -e "$TMP/save3" ]]; then ok "보고서 없음은 건너뜀"
else fail "보고서 없음은 건너뜀" "종료 $code"; fi

# 4. 같은 이름이 있으면 덮지 않고 -HHMMSS 접미사
dir="$TMP/save4"
bash "$SAVE" "$OUT" "$TAG" "$dir" 2>/dev/null
bash "$SAVE" "$OUT" "$TAG" "$dir" 2>/dev/null; code=$?
extra="$(ls "$dir" | grep -cE "^$NAME-[0-9]{6}\.json$")"
if [[ $code == 0 && -e "$dir/$NAME.json" && "$extra" == 1 ]]; then ok "이름 충돌은 시각 접미사"
else fail "이름 충돌은 시각 접미사" "종료 $code, 폴더 '$(ls "$dir")'"; fi

# 5. 태그가 16진 7자 이상이 아니면 64, 아무것도 쓰지 않음
for bad in "" abc "../../x0123456" "current"; do
  bash "$SAVE" "$OUT" "$bad" "$TMP/save5" 2>/dev/null; code=$?
  if [[ $code == 64 && ! -e "$TMP/save5" ]]; then ok "잘못된 태그 '$bad'"
  else fail "잘못된 태그 '$bad'" "종료 $code"; fi
done

echo "실패 $fails"
(( fails == 0 ))
