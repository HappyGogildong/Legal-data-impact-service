"use client";

import { useQuery } from "@tanstack/react-query";
import { BOOKMARKS_KEY, fetchBookmarks } from "@/lib/api/bookmarks";

/** 내 북마크 목록 — 로그인일 때만 부른다. */
export function useBookmarks(enabled: boolean) {
  return useQuery({
    queryKey: BOOKMARKS_KEY,
    queryFn: fetchBookmarks,
    enabled,
    staleTime: 5 * 60_000,
  });
}
