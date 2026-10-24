import Link from "next/link";

/** 법령을 특정하지 않은 질문(법령 발견, S04 — 3단계)의 임시 안내. */
export function DiscoveryNotice() {
  return (
    <div className="mx-auto max-w-[640px] px-4 py-10 text-center">
      <h1 className="font-bold">법 이름 없이 찾기는 곧 제공돼요</h1>
      <p className="text-sm text-muted-foreground">
        법령 이름을 넣어 다시 물어봐 주세요. 예) 주택법 바뀌면 전세 계약 어떻게
        돼?
      </p>
      <Link
        href="/"
        className="mt-3 inline-block text-[12.5px] font-bold text-navy"
      >
        질문 고치기
      </Link>
    </div>
  );
}
