"use client";

import { useAuth } from "@/features/auth";
import { useNotices } from "../model/useNotices";
import { Hero } from "./Hero";
import { NoticeBoard } from "./NoticeBoard";
import { SignupBanner } from "./SignupBanner";
import { StartHere } from "./StartHere";
import { WelcomeBack } from "./WelcomeBack";

/**
 * 첫 화면 (#72, #231, #261, #263, #275).
 *
 * **처음 온 사람과 매일 오는 사람에게 다른 것을 보여준다.** 소개는 처음 온 사람에게만
 * 필요하고, 이미 쓰는 사람에게는 "오늘 무엇을 할지" 가 필요하다.
 *
 * 그 아래는 두 사람에게 같다 — 공지와 풀 문제는 누구에게나 첫 화면의 내용이다.
 * 둘을 **2단으로 나란히** 둔다. 세로로 쌓으면 위(Hero)는 본문 폭을 다 쓰는데 아래만
 * 좁아, 같은 화면에서 폭이 두 번 바뀐다.
 */
export function HomePage() {
  const { user, loading } = useAuth();
  const notices = useNotices();

  // 공지가 없으면 문제 목록이 폭을 다 쓴다 (#275). 빈 칸을 남기면 고장으로 보이고,
  // "공지사항 없음" 상자를 만들지 않기로 한 결정(#263)도 그대로 지킨다.
  const twoColumn = notices !== null && notices.length > 0;

  return (
    <div className="space-y-12">
      {/* 판정 전에는 아무것도 그리지 않는다 — 소개가 떴다 사라지면 그것이 더 어수선하다. */}
      {loading ? null : user ? <WelcomeBack /> : <Hero />}

      <div className={twoColumn ? "grid gap-8 lg:grid-cols-2" : ""}>
        <StartHere />
        {twoColumn ? <NoticeBoard notices={notices} /> : null}
      </div>

      {/* 로그인한 사람에게 회원가입을 권하지 않는다 (#73). */}
      {loading || user ? null : <SignupBanner />}
    </div>
  );
}
