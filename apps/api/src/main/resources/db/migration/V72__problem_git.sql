-- Git 문제 (#654).
--
-- **Redis(#455)와 모양이 같고 담기는 것이 다르다.** 시드 → 정답 → 확인이라는 뼈대는
-- 같지만 여기는 git 명령이고 저기는 redis 명령이다. 그리고 하네스가 해야 하는 일이
-- 다르다 — 커밋 해시가 재현되도록 신원과 시각을 고정하고, 네트워크 명령을 즉시 막는다.
ALTER TABLE problems DROP CONSTRAINT IF EXISTS problems_problem_kind_check;
ALTER TABLE problems ADD CONSTRAINT problems_problem_kind_check
    CHECK (problem_kind IN ('JUDGE_STDIO', 'JUDGE_SQL', 'JUDGE_REDIS', 'JUDGE_MONGODB',
                            'JUDGE_INTERACTIVE', 'JUDGE_FUNCTION', 'JUDGE_REGEX',
                            'JUDGE_GIT', 'QUIZ', 'MANUAL'));

CREATE TABLE problem_git_specs (
    problem_id BIGINT PRIMARY KEY REFERENCES problems (id) ON DELETE CASCADE,
    -- 시작 저장소를 만드는 명령. 문제가 소유한다. 없으면 빈 저장소에서 시작한다.
    seed_commands   TEXT,
    -- 정답 명령. 기대 상태를 만든다.
    answer_commands TEXT NOT NULL,
    /*
        끝난 뒤를 읽는 명령. **선택이 아니다** — 이것이 없으면 무엇을 정답으로 볼지가 없다.

        **커밋 해시를 그대로 찍는 것은 권하지 않는다.** 하네스가 신원·시각을 고정해
        해시가 재현되기는 하지만, 메시지 한 글자만 달라도 해시가 달라져 **같은 결과에
        이른 다른 풀이가 틀린 답**이 된다. 트리 해시(`%T`)와 그래프 모양은 내용만 본다.
    */
    verify_commands TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
