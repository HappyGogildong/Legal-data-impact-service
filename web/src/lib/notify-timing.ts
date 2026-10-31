/** 알림 시점(D74 ②) — 시행일 며칠 전. 화면 표기와 기본값. */
export const NOTIFY_OPTIONS: { value: number; label: string }[] = [
  { value: 0, label: "당일" },
  { value: 1, label: "1일 전" },
  { value: 7, label: "7일 전" },
  { value: 30, label: "30일 전" },
];

export const DEFAULT_NOTIFY_BEFORE = 7;

export function timingLabel(notifyBefore: number): string {
  return (
    NOTIFY_OPTIONS.find((option) => option.value === notifyBefore)?.label ??
    `${notifyBefore}일 전`
  );
}
