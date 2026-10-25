import type { Metadata } from "next";
import { ProfileScreen } from "@/components/profile/profile-screen";

export const metadata: Metadata = {
  title: "내 프로필",
  robots: { index: false },
};

export default function ProfilePage() {
  return <ProfileScreen />;
}
