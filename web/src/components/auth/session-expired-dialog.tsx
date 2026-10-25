"use client";

import { usePathname, useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { setReturnTo } from "@/lib/return-to";

/** S16 세션 만료 — 작업 중 401. 로그인하면 하던 화면으로 돌아온다. */
export function SessionExpiredDialog({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const router = useRouter();
  const pathname = usePathname();
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>다시 로그인해 주세요</DialogTitle>
          <DialogDescription>
            로그인이 만료됐어요. 로그인하면 하던 화면으로 돌아와요.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            닫기
          </Button>
          <Button
            onClick={() => {
              setReturnTo(pathname);
              router.push("/login");
            }}
          >
            로그인
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
