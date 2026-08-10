-- 문제마다 채점 우선순위를 정할 수 있게 한다 (#102).
--
-- 실행이 무거운 문제가 큐를 오래 잡으면 그 문제 하나 때문에 다른 모든 문제의 채점이
-- 밀린다. 그런 문제만 뒤로 미룰 수 있어야 한다.
--
-- HIGH 가 없는 이유: 최상위는 시스템 동작(정답 코드 검증)에만 남긴다. 어드민이 문제를
-- 최상위로 올릴 수 있으면 결국 모든 문제가 그리 되고 등급이 의미를 잃는다.
ALTER TABLE problems
    ADD COLUMN judge_priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
        CHECK (judge_priority IN ('NORMAL', 'LOW'));
