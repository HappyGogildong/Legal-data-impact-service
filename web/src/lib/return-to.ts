const KEY = "lia.returnTo";

/** 같은 출처 경로만 — "//host"·"/\host" 는 브라우저가 외부 주소로 해석한다(오픈 리다이렉트 방지). */
export function isSafePath(path: string | null | undefined): path is string {
  return (
    !!path &&
    path.startsWith("/") &&
    !path.startsWith("//") &&
    !path.startsWith("/\\")
  );
}

/** 로그인을 시작하는 곳이 부른다 — 로그인·동의·프로필을 마치면 이 경로로 돌아온다(screen-plan S07). */
export function setReturnTo(path: string) {
  try {
    sessionStorage.setItem(KEY, path);
  } catch {
    // 저장소를 못 쓰면 홈으로 돌아온다
  }
}

/** 읽기만 한다 — effect(StrictMode 에서 두 번)에서도 안전. 지우기는 마지막 이동을 하는 이벤트 처리기에서. */
export function readReturnTo(): string | null {
  try {
    const path = sessionStorage.getItem(KEY);
    return isSafePath(path) ? path : null;
  } catch {
    return null;
  }
}

export function clearReturnTo() {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // 무시
  }
}
