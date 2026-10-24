"use client";

import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { fetchProfile } from "@/lib/api/auth";
import type { Unmet } from "@/lib/api/types";
import { setReturnTo } from "@/lib/return-to";
import { PROFILE_KEY, useSession } from "@/lib/session";

const BUTTON =
  "mt-2 rounded-md bg-navy px-3 py-1.5 text-[12.5px] font-bold text-white";

/**
 * "내 영향·대응 방안" 미충족 카드의 다음 행동 — 상태별(frontend-stack §5).
 * 분석은 아직 프로필을 읽지 않으므로(D60 슬라이스 ③) 프로필이 있으면 "곧 제공"이라고만 말한다.
 */
export function PersonalizeAction({ unmet }: { unmet?: Unmet }) {
  const router = useRouter();
  const session = useSession();
  const signedIn = session.data != null;
  const profile = useQuery({
    queryKey: PROFILE_KEY,
    queryFn: fetchProfile,
    enabled: signedIn,
  });

  if (unmet?.reason !== "PROFILE_REQUIRED" || session.isPending) return null;
  const go = (path: string) => {
    setReturnTo("/ask");
    router.push(path);
  };

  if (!signedIn) {
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
  if (profile.isPending) return null;
  if (!profile.data) {
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
  return (
    <p className="text-muted-foreground">
      프로필을 반영한 내 영향·대응 방안은 곧 제공돼요.
    </p>
  );
}
