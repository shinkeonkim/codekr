/*
    모범 답안 (#719).

    **푼 뒤에 볼 것이 없었다.** 맞히면 끝이고, 틀리면 왜 틀렸는지 알 방법이 없다.
    객관식(#650)만 해설이 있고 나머지 아홉 유형에는 아무것도 없다.

    ## 채점에 쓰는 정답과 **다른 표**에 둔다

    Git·SQL·Redis·MongoDB 는 기대값이 `answer_commands`·`answer_sql` 에서 만들어진다.
    그 칸을 모범 답안으로 겸하면 **읽기 좋게 다듬는 것이 채점 기준을 바꾸는 일**이
    된다. 모범 답안은 읽으라고 쓰고 채점 정답은 돌아가라고 쓴다 — 한 칸에 두면 싸운다.

    `problems.solution_source_code`(#39) 도 안 쓴다. 그쪽은 채점을 안 바꾸지만
    **검증 지문에 들어 있어서**, 오타 하나를 고치면 그 문제가 "검증되지 않은 문제" 가
    된다. 설명을 다듬을 때마다 정답 코드를 다시 돌려야 한다면 아무도 다듬지 않는다.

    ## 정규식·뮤테이션이 정답을 안 두기로 한 판단과 부딪히지 않는다

    그 둘은 "출제자가 실수한 패턴이 곧 정답이 된다" 는 이유로 정답을 저장하지 않는다
    (#652, #653). **그 이유는 채점에 쓸 때만 성립한다.** 여기 담기는 것은 판정에
    관여하지 않으므로 그 위험이 없다.
*/
CREATE TABLE problem_editorials (
    problem_id BIGINT PRIMARY KEY REFERENCES problems (id) ON DELETE CASCADE,

    -- 풀이 설명. 마크다운이다.
    body TEXT NOT NULL,

    /*
        참고 답안. **선택 사항이다.**

        유형마다 담기는 것이 다르다 — 코드일 수도, git 명령일 수도, 쿼리일 수도 있다.
        무엇인지는 `reference_label` 이 말한다. 구조를 유형별로 나누지 않는 이유는
        **여기 담긴 것을 아무도 실행하지 않기 때문**이다. 실행할 것이면 유형별 스펙
        표로 가야 한다.
    */
    reference_answer TEXT,
    reference_label  VARCHAR(60),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
