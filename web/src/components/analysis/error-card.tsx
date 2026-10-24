import Link from "next/link";
import { ApiError } from "@/lib/api/errors";

/** 시스템 오류(S16 서버·네트워크 오류의 화면 안 버전). 400 은 질문 문제라 고치기로. */
export function ErrorCard({
  error,
  onRetry,
}: {
  error: unknown;
  onRetry: () => void;
}) {
  const badRequestMessage =
    error instanceof ApiError && error.status === 400 ? error.message : null;
  return (
    <div role="alert" className="mx-auto max-w-[640px] px-4 py-12 text-center">
      <p aria-hidden className="text-3xl">
        ⚡
      </p>
      <h1 className="mt-2 font-bold">
        {badRequestMessage ? "질문을 확인해 주세요" : "잠시 문제가 생겼어요"}
      </h1>
      <p className="text-sm text-muted-foreground">
        {badRequestMessage ??
          "잠시 후 다시 시도해 주세요. 계속되면 안내 페이지의 문의처로 알려 주세요."}
      </p>
      {badRequestMessage ? (
        <Link
          href="/"
          className="mt-3 inline-block text-[12.5px] font-bold text-navy"
        >
          질문 고치기
        </Link>
      ) : (
        <button
          type="button"
          onClick={onRetry}
          className="mt-3 rounded-md bg-navy px-4 py-2 text-[13px] font-bold text-white"
        >
          다시 시도
        </button>
      )}
    </div>
  );
}
