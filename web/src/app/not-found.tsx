import Link from "next/link";
import { QuestionBox } from "@/components/question/question-box";

export default function NotFound() {
  return (
    <div className="mx-auto max-w-[480px] px-4 py-12 text-center">
      <p aria-hidden className="text-3xl">
        🧭
      </p>
      <h1 className="mt-2 font-bold">찾는 페이지가 없어요</h1>
      <p className="text-sm text-muted-foreground">
        주소가 바뀌었거나 지워진 페이지예요.
      </p>
      <Link
        href="/"
        className="mt-3 mb-4 inline-block rounded-md bg-navy px-4 py-2 text-[13px] font-bold text-white"
      >
        홈으로
      </Link>
      <QuestionBox />
    </div>
  );
}
