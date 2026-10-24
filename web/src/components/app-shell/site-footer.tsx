import Link from "next/link";

/** 모든 화면 하단 — 면책 + 안내 링크(screen-plan §1). */
export function SiteFooter() {
  return (
    <footer className="mb-12 border-t border-line-soft bg-white md:mb-0">
      <div className="mx-auto max-w-[1120px] px-4 py-3 text-[11px] text-muted-foreground">
        법률 자문이 아닌 참고용 정보입니다. ·{" "}
        <Link href="/info" className="underline">
          이용 안내·개인정보처리방침
        </Link>
      </div>
    </footer>
  );
}
