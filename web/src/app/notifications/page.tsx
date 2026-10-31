import type { Metadata } from "next";
import { NotificationListView } from "@/components/notifications/notification-list-view";

export const metadata: Metadata = {
  title: "알림",
  robots: { index: false },
};

export default function NotificationsPage() {
  return <NotificationListView />;
}
