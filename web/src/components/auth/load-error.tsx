/** 보호 화면의 조회 실패(5xx·네트워크) — 불러오는 중에 멈추지 않게. */
export function LoadError({ onRetry }: { onRetry: () => void }) {
  return (
    <div role="alert" className="px-4 py-12 text-center text-sm">
      <p className="text-muted-foreground">정보를 불러오지 못했어요.</p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-2 text-[12.5px] font-bold text-navy"
      >
        다시 시도
      </button>
    </div>
  );
}
