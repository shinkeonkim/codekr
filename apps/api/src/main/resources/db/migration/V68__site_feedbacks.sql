-- 사이트 신고·제안 (#603).
--
-- 푸터의 "문제 신고·제안" 이 **GitHub 이슈 목록으로 나가고 있었다.** 계정이 있어야 하고,
-- 로그인해야 하고, 공개된 곳에 쓰는 일이다 — 코딩 테스트를 풀러 온 사람에게 요구할
-- 일이 아니고, 사실상 안 받는 것에 가깝다.
--
-- **`problem_reports`(#478)를 넓히지 않고 새로 뒀다.** 그쪽은 `problem_id` 가 NOT NULL
-- 이고 종류가 전부 문제 내용에 대한 것(테스트케이스 부족·제약 누락·지문 오류)이다.
-- 여기에 "기능 제안" 을 섞으면 **한 enum 에 두 축이 들어가고**, 어드민 목록이 행마다
-- "이건 문제에 매인 것인가" 를 따져야 한다. 들어오는 자리도 다르다 — 저기는 문제
-- 화면, 여기는 푸터.
CREATE TABLE site_feedbacks (
    id          BIGSERIAL PRIMARY KEY,
    -- **로그인한 사람만 넣는다.** 막을 것(속도 제한·캡차)이 없는 채로 열면 스팸이
    -- 그대로 쌓이고, 처리 결과를 되돌려 줄 곳도 없다.
    reporter_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- BUG(안 된다) | SUGGESTION(이렇게 해 달라) | OTHER
    kind        VARCHAR(30) NOT NULL,
    -- 어디에서 겪었는지. 어드민이 재현하려면 화면이 어디였는지가 있어야 한다.
    page_url    VARCHAR(500),
    body        TEXT        NOT NULL,
    -- OPEN → ACCEPTED(반영했다) | REJECTED(안 한다)
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    -- 처리하며 남긴 말. **거절에는 반드시 있어야 한다** — 이유 없는 거절은
    -- 넣은 사람에게 "읽지 않았다" 와 구분되지 않는다 (#478 과 같은 규칙).
    resolution  TEXT,
    resolved_by BIGINT REFERENCES users (id),
    resolved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 어드민은 "아직 안 본 것" 부터 본다.
CREATE INDEX idx_site_feedbacks_open ON site_feedbacks (status, created_at DESC);
