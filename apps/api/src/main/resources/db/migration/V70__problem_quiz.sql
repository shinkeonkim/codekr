-- 개념 퀴즈 (#650).
--
-- **자리는 #59 때 이미 잡혀 있었다** — `problem_kind` 의 CHECK 에 `QUIZ` 가 들어 있고
-- (V67), `ProblemKind.QUIZ` 도 `ready = false` 로 선언돼 있었다. 여기서 채우는 것은
-- 그 유형이 담을 자료다.
--
-- **다른 유형과 가장 다른 점: 실행기를 쓰지 않는다.** 채점이 값 비교라 api 가 즉시 한다.
-- 그래서 시간·메모리 제한도, 런타임도, 테스트케이스도 없다.

-- 문제 단위의 설정.
CREATE TABLE problem_quiz_specs (
    problem_id  BIGINT PRIMARY KEY REFERENCES problems (id) ON DELETE CASCADE,
    -- SINGLE  : 보기 중 하나
    -- MULTIPLE: 보기 중 여럿 (**정확히 일치해야 정답이다** — 부분 점수는 #473 이 정한다)
    -- SHORT   : 짧은 글자
    answer_type VARCHAR(20) NOT NULL CHECK (answer_type IN ('SINGLE', 'MULTIPLE', 'SHORT')),
    -- 채점이 끝난 뒤에만 보여 준다. **틀린 사람에게 왜 틀렸는지 말해 주는 자리다** —
    -- 코드 문제는 판정 자체가 설명이 되지만 퀴즈는 그렇지 않다.
    explanation TEXT,
    /*
        단답의 정규화 (#650).

        **문제마다 다르다.** `TCP` 와 `tcp` 는 같게 보고 싶지만, 대소문자가 뜻을
        가르는 문제도 있다(`chmod` 의 `X` 와 `x`). 전역 규칙 하나로 정하면 그런 문제를
        낼 수 없다.

        동의어(`전송 제어 프로토콜`)는 정규화가 아니라 **정답을 여럿 두는 것**으로
        푼다 — `problem_quiz_answers` 가 그 자리다.
    */
    ignore_case       BOOLEAN NOT NULL DEFAULT true,
    ignore_whitespace BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 객관식 보기. **SINGLE·MULTIPLE 만 쓴다.**
--
-- `correct` 는 사용자에게 **절대 나가지 않는다** — 응답 DTO 가 담지 않는다.
-- 그것이 새면 개발자 도구를 여는 것만으로 문제가 무너진다.
CREATE TABLE problem_quiz_choices (
    id         BIGSERIAL PRIMARY KEY,
    problem_id BIGINT  NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    seq        INT     NOT NULL,
    content    TEXT    NOT NULL,
    correct    BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (problem_id, seq)
);

-- 단답으로 받아 줄 답. **SHORT 만 쓴다.**
--
-- **보기와 한 표에 담지 않는다.** 담으면 `correct = true` 인 행이 어떤 유형에서는
-- 보기이고 어떤 유형에서는 정답 글자가 되어, 행마다 "이건 무엇인가" 를 따져야 한다.
-- #455 가 유형 이름에서, #603 이 신고 표에서 같은 이유로 나눴다.
CREATE TABLE problem_quiz_answers (
    id         BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    seq        INT    NOT NULL,
    content    TEXT   NOT NULL,
    UNIQUE (problem_id, seq)
);
