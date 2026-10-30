import type { Metadata } from "next";
import { BookmarkListView } from "@/components/bookmarks/bookmark-list-view";

export const metadata: Metadata = {
  title: "북마크",
  robots: { index: false },
};

export default function BookmarksPage() {
  return <BookmarkListView />;
}
