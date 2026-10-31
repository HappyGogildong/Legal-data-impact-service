/** 주 메뉴 — 홈·알림(S11, D74)·안내·내 정보(frontend-stack §5). 익명이 내 정보를 누르면 로그인으로. */
export const NAV_ITEMS: { href: string; label: string }[] = [
  { href: "/", label: "홈" },
  { href: "/notifications", label: "알림" },
  { href: "/info", label: "안내" },
  { href: "/me", label: "내 정보" },
];

export function isActive(pathname: string, href: string): boolean {
  return href === "/"
    ? pathname === "/"
    : pathname === href || pathname.startsWith(href + "/");
}
