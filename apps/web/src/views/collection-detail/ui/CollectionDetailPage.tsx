"use client";

import { collectionApi } from "@/entities/collection";
import type { CollectionDetail } from "@/entities/collection";
import { ApiError } from "@/shared/api";
import { EmptyState, useToast } from "@/shared/ui";
import { use, useEffect, useState } from "react";
import { CollectionDetailView } from "./CollectionDetailView";

export function CollectionDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return <Detail load={() => collectionApi.detail(Number(id))} />;
}

export function SharedCollectionPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = use(params);
  return <Detail load={() => collectionApi.shared(decodeURIComponent(token))} />;
}

function Detail({ load }: { load: () => Promise<CollectionDetail> }) {
  const toast = useToast();
  const [detail, setDetail] = useState<CollectionDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    load()
      .then(setDetail)
      .catch((caught) =>
        setError(caught instanceof ApiError ? caught.message : "문제집을 불러오지 못했습니다."),
      );
    // load 는 매 렌더 새로 만들어지므로 의존성에 두지 않는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (error) return <EmptyState title={error} />;
  if (!detail) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  const copyLink = () => {
    const token = detail.summary.shareToken;
    if (!token) return;
    navigator.clipboard
      .writeText(`${window.location.origin}/collections/shared/${token}`)
      .then(() => toast.success("공유 링크를 복사했습니다."))
      .catch(() => toast.error("복사하지 못했습니다."));
  };

  return <CollectionDetailView detail={detail} onCopyLink={copyLink} />;
}
