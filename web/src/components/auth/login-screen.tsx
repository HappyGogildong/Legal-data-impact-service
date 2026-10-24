"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { readReturnTo } from "@/lib/return-to";

/** 소셜 로그인 — 시작은 전체 페이지 이동(<a>). <Link> 미리 불러오기가 OAuth 를 건드리지 않게. */
const PROVIDERS = [
  {
    id: "kakao",
    label: "카카오로 계속하기",
    className: "bg-[#FEE500] text-[#191919]",
  },
  {
    id: "naver",
    label: "네이버로 계속하기",
    className: "bg-[#03C75A] text-white",
  },
  {
    id: "google",
    label: "Google로 계속하기",
    className: "border border-border bg-white text-ink",
  },
];

const ERROR_TEXT: Record<string, string> = {
  oauth: "취소했거나 잠시 문제가 있었어요.",
  identity: "계정 정보를 확인하지 못했어요. 다른 방법으로 로그인해 주세요.",
  session: "로그인 상태를 확인하지 못했어요. 다시 시도해 주세요.",
};

/** S07 — 로그인은 개인화 기능에만 필요하다고 알린다. 실패·취소는 안내 후 돌아갈 길을 준다. */
export function LoginScreen() {
  const router = useRouter();
  const error = useSearchParams().get("error");

  if (error) {
    return (
      <div className="mx-auto max-w-[420px] px-4 py-12 text-center">
        <h1 className="text-lg font-bold">로그인이 완료되지 않았어요</h1>
        <p className="text-sm text-muted-foreground">
          {ERROR_TEXT[error] ?? ERROR_TEXT.oauth}
        </p>
        <div className="mt-4 flex flex-col gap-2">
          <button
            type="button"
            onClick={() => router.replace("/login")}
            className="rounded-md bg-navy py-2.5 text-[13px] font-bold text-white"
          >
            다시 시도
          </button>
          <button
            type="button"
            onClick={() => router.push(readReturnTo() ?? "/")}
            className="rounded-md border border-border bg-white py-2.5 text-[13px] font-bold"
          >
            로그인 없이 계속 보기
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-[420px] px-4 py-10">
      <h1 className="text-lg font-bold">내 상황에 맞춘 분석을 받아 보세요</h1>
      <p className="text-sm text-muted-foreground">
        로그인은 내 영향·대응 방안·알림·이력 기능에만 필요해요. 요약·변경점은
        로그인 없이도 볼 수 있어요.
      </p>
      <div className="mt-5 flex flex-col gap-2">
        {PROVIDERS.map((provider) => (
          <a
            key={provider.id}
            href={`/oauth2/authorization/${provider.id}`}
            className={`rounded-md py-3 text-center text-[13.5px] font-bold ${provider.className}`}
          >
            {provider.label}
          </a>
        ))}
      </div>
    </div>
  );
}
