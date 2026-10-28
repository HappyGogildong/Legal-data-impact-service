"use client";

import { useRouter } from "next/navigation";
import type { Unmet } from "@/lib/api/types";
import { setReturnTo } from "@/lib/return-to";
import { useSession } from "@/lib/session";

const BUTTON =
  "mt-2 rounded-md bg-navy px-3 py-1.5 text-[12.5px] font-bold text-white";

/**
 * "내 영향·대응 방안" 미충족 카드의 다음 행동 — 사유·로그인 상태별(frontend-stack §5, D69).
 * PROFILE_REQUIRED: 익명 → 로그인, 로그인 → 프로필 입력(없음·빈 프로필 모두). CONSENT_OUTDATED → 재동의.
 * 그 외 사유는 문구만(unmetCopy).
 */
export function PersonalizeAction({ unmet }: { unmet?: Unmet }) {
  const router = useRouter();
  const session = useSession();
  if (session.isPending) return null;
  const go = (path: string) => {
    setReturnTo("/ask");
    router.push(path);
  };

  if (unmet?.reason === "CONSENT_OUTDATED") {
    return (
      <button type="button" onClick={() => go("/consent")} className={BUTTON}>
        동의하기
      </button>
    );
  }
  if (unmet?.reason !== "PROFILE_REQUIRED") return null;
  if (session.data == null) {
    return (
      <>
        <p className="text-muted-foreground">
          로그인하고 프로필을 입력하면 내 영향과 대응 방안을 알려드려요.
        </p>
        <button type="button" onClick={() => go("/login")} className={BUTTON}>
          로그인하기
        </button>
      </>
    );
  }
  return (
    <>
      <p className="text-muted-foreground">
        프로필을 입력하면 내 영향과 대응 방안을 알려드려요.
      </p>
      <button type="button" onClick={() => go("/profile")} className={BUTTON}>
        프로필 입력하기
      </button>
    </>
  );
}
