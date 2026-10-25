/**
 * 로그아웃·계정 삭제 뒤 전체 페이지 이동 — 메모리의 캐시·화면 상태를 한 번에 버린다.
 */
export function reloadTo(path: string) {
  window.location.replace(path);
}
