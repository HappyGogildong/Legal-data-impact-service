import type { Metadata } from "next";
import { MyScreen } from "@/components/me/my-screen";

export const metadata: Metadata = {
  title: "내 정보",
  robots: { index: false },
};

export default function MyPage() {
  return <MyScreen />;
}
