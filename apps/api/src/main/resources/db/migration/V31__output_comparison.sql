-- 출력 비교 방식과 허용 오차 (#279, ADR-0010).
--
-- **기본값은 지금 동작(정확 일치)이다.** 이미 등록된 문제의 판정이 하나도 바뀌면 안 된다 —
-- 비교 방식이 바뀌면 재채점(#107)에서 판정이 뒤집힌다.
ALTER TABLE problems
    ADD COLUMN output_comparison VARCHAR(16) NOT NULL DEFAULT 'EXACT',
    -- 절대·상대 오차 중 하나만 만족해도 맞다고 본다. EXACT 에서는 쓰이지 않는다.
    ADD COLUMN float_epsilon DOUBLE PRECISION NOT NULL DEFAULT 0;

-- 값 오타로 채점 방식이 조용히 바뀌지 않게 막는다.
ALTER TABLE problems
    ADD CONSTRAINT problems_output_comparison_check
        CHECK (output_comparison IN ('EXACT', 'FLOAT'));

-- 오차는 음수일 수 없다. 음수면 모든 비교가 실패해 전부 오답이 된다.
ALTER TABLE problems
    ADD CONSTRAINT problems_float_epsilon_check CHECK (float_epsilon >= 0);
