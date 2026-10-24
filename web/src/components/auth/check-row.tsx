import { useId } from "react";

/** 필수 확인 체크 한 줄 — 기본 checkbox(키보드·스크린리더 그대로). */
export function CheckRow({
  checked,
  onChange,
  children,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  children: React.ReactNode;
}) {
  const id = useId();
  return (
    <div className="my-2.5 flex items-start gap-2.5 text-[13.5px]">
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
        className="mt-0.5 size-[18px] accent-navy focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-navy"
      />
      <label htmlFor={id}>{children}</label>
    </div>
  );
}
