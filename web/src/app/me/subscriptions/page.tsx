import type { Metadata } from "next";
import { SubscriptionListView } from "@/components/subscriptions/subscription-list-view";

export const metadata: Metadata = {
  title: "구독 관리",
  robots: { index: false },
};

export default function SubscriptionsPage() {
  return <SubscriptionListView />;
}
