"use client"; // 오류 경계는 클라이언트 컴포넌트

import { useEffect } from "react";

/** S16 서버·네트워크 오류 — Next 16 오류 경계 props 는 { error, retry }. */
export default function ErrorPage({
  error,
  retry,
}: {
  error: unknown;
  retry: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div role="alert" className="mx-auto max-w-[480px] px-4 py-12 text-center">
      <p aria-hidden className="text-3xl">
        ⚡
      </p>
      <h1 className="mt-2 font-bold">잠시 문제가 생겼어요</h1>
      <p className="text-sm text-muted-foreground">
        잠시 후 다시 시도해 주세요. 계속되면 안내 페이지의 문의처로 알려 주세요.
      </p>
      <button
        type="button"
        onClick={() => retry()}
        className="mt-3 rounded-md bg-navy px-4 py-2 text-[13px] font-bold text-white"
      >
        다시 시도
      </button>
    </div>
  );
}
