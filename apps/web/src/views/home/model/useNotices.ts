"use client";

import { postApi } from "@/entities/post";
import type { PostSummary } from "@/entities/post";
import { useEffect, useState } from "react";

/** 첫 화면에 몇 개까지 보일지. 옆의 문제 목록과 높이를 맞춘 수다. */
export const NOTICE_LIMIT = 5;

/**
 * 첫 화면의 공지 (#263, #275).
 *
 * **불러오기를 화면에서 떼어 냈다.** 공지가 없으면 문제 목록이 폭을 다 써야 하는데
 * (#275), 그 판단은 두 단을 함께 보는 쪽만 할 수 있다. 목록 컴포넌트 안에 불러오기가
 * 있으면 바깥은 "비었는지" 를 알 길이 없다.
 *
 * `null` 은 **아직 모른다**, 빈 배열은 **없다** 이다. 둘을 같게 다루면 불러오는 동안
 * 한 단짜리로 그렸다가 두 단으로 튄다.
 */
export function useNotices(): PostSummary[] | null {
  const [notices, setNotices] = useState<PostSummary[] | null>(null);

  useEffect(() => {
    postApi
      .list({ board: "NOTICE", size: NOTICE_LIMIT })
      .then((page) => setNotices(page.content))
      // 공지를 못 불러온 것으로 첫 화면 전체가 멈추면 안 된다. 없는 것으로 둔다.
      .catch(() => setNotices([]));
  }, []);

  return notices;
}
