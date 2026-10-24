"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { agreeProfileConsent } from "@/lib/api/auth";
import { CONSENTS_KEY } from "@/lib/session";
import { CheckRow } from "./check-row";

/** 프로필 저장이 409(동의 없음·옛 버전)일 때 — 화면을 떠나지 않고 동의한 뒤 저장을 다시 시도한다. */
export function ReconsentDialog({
  open,
  onAgreed,
  onClose,
}: {
  open: boolean;
  onAgreed: () => void;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [over14, setOver14] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const agree = useMutation({
    mutationFn: agreeProfileConsent,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CONSENTS_KEY });
      onAgreed();
    },
  });
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>프로필을 저장하려면 동의가 필요해요</DialogTitle>
          <DialogDescription>
            개인정보처리방침이 바뀌었거나 아직 동의하지 않았어요.
          </DialogDescription>
        </DialogHeader>
        <CheckRow checked={over14} onChange={setOver14}>
          만 14세 이상입니다{" "}
          <span className="text-muted-foreground">(필수)</span>
        </CheckRow>
        <CheckRow checked={agreed} onChange={setAgreed}>
          프로필 수집·이용에 동의합니다{" "}
          <span className="text-muted-foreground">(필수)</span>
        </CheckRow>
        {agree.isError && (
          <p className="text-sm text-removed">
            동의를 저장하지 못했어요. 다시 시도해 주세요.
          </p>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            나중에
          </Button>
          <Button
            disabled={!over14 || !agreed || agree.isPending}
            onClick={() => agree.mutate()}
          >
            동의하고 저장
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
