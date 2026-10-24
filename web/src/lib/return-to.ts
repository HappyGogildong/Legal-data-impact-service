const KEY = "lia.returnTo";

/**
 * 같은 출처 경로만 — "//host"·"/\host" 는 브라우저가 외부 주소로 해석한다(오픈 리다이렉트 방지).
 * 탭·줄바꿈은 브라우저가 URL 에서 지워 "/\t/host" 가 "//host" 가 되므로 함께 거른다.
 */
export function isSafePath(path: string | null | undefined): path is string {
  return (
    !!path &&
    path.startsWith("/") &&
    !path.startsWith("//") &&
    !path.startsWith("/\\") &&
    !/[\t\r\n]/.test(path)
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

/**
 * 마지막 이동을 하는 이벤트 처리기가 부른다 — 읽고, 지우고, 돌려준다.
 * 지금 화면(currentPath)과 같은 경로면 제자리 이동이라 null(저장·건너뛰기가 막히는 것을 방지).
 */
export function takeReturnTo(currentPath: string): string | null {
  const path = readReturnTo();
  clearReturnTo();
  return path === currentPath ? null : path;
}
