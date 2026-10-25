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
import { isUnauthorized } from "@/lib/api/errors";
import { CONSENTS_KEY } from "@/lib/session";
import { CheckRow } from "./check-row";

/**
 * 프로필 저장이 409(동의 없음·옛 버전)일 때 — 화면을 떠나지 않고 동의한 뒤 저장을 다시 시도한다.
 * 동의 중 세션이 만료(401)되면 대화상자를 닫고 onExpired 로 알린다(호출자가 다시 로그인 안내를 띄운다).
 * 나이 확인은 서버가 매번 요구하므로, 닫거나 동의에 성공하면 두 체크를 비운다.
 */
export function ReconsentDialog({
  open,
  onAgreed,
  onClose,
  onExpired,
}: {
  open: boolean;
  onAgreed: () => void;
  onClose: () => void;
  onExpired: () => void;
}) {
  const queryClient = useQueryClient();
  const [over14, setOver14] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const agree = useMutation({
    mutationFn: agreeProfileConsent,
    onSuccess: () => {
      setOver14(false);
      setAgreed(false);
      queryClient.invalidateQueries({ queryKey: CONSENTS_KEY });
      onAgreed();
    },
    onError: (error) => {
      if (isUnauthorized(error)) {
        close();
        onExpired();
      }
    },
  });
  const close = () => {
    setOver14(false);
    setAgreed(false);
    agree.reset(); // 다시 열었을 때 이전 오류 문구가 남지 않게
    onClose();
  };
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) close();
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
        {agree.isError && !isUnauthorized(agree.error) && (
          <p className="text-sm text-removed">
            동의를 저장하지 못했어요. 다시 시도해 주세요.
          </p>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={close}>
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
