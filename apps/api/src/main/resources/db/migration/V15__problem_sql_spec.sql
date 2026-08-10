-- SQL 문제의 스펙 (#60).
--
-- 유형별 데이터를 JSON 한 컬럼에 몰지 않고 테이블로 나눈다 (기획서 §3).
-- 문제 하나에 스펙 하나이므로 problem_id 가 곧 기본키다.
CREATE TABLE problem_sql_specs (
    problem_id BIGINT PRIMARY KEY REFERENCES problems (id) ON DELETE CASCADE,
    -- 스키마와 시드 데이터. 슈퍼유저로 주입한다. 출제자가 소유한다.
    schema_sql TEXT        NOT NULL,
    -- **정답을 결과 집합이 아니라 쿼리로 저장한다.** 시드가 바뀌면 기대 결과도 따라간다.
    answer_sql TEXT        NOT NULL,
    -- 행 순서를 무시할지. 기본은 무시다 — 문제가 정렬을 요구하지 않는데 순서를 비교하면
    -- 맞는 답이 틀린 것으로 나온다. 정렬이 문제의 일부인 경우에만 끈다.
    ignore_row_order BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
