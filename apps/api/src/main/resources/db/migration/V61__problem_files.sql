-- 여러 파일을 완성하는 문제 (#457, #497).
--
-- **설계를 물으려면 파일이 여럿이어야 한다.** 클래스 몇 개가 서로를 쓰는 구조,
-- 인터페이스와 구현, 모듈 경계 — 한 파일에 몰아넣으면 묻고 싶은 것이 사라진다.
--
-- **파일 목록은 런타임마다다.** 같은 문제라도 자바는 `Main.java`·`Helper.java` 이고
-- 파이썬은 `main.py`·`helper.py` 다. 시작 코드(`problem_templates`)가 런타임마다인
-- 것과 같은 이유이고, 실제로 이 표는 그것의 확장이다.
CREATE TABLE problem_files (
    id         BIGSERIAL PRIMARY KEY,
    problem_id BIGINT      NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    runtime_id VARCHAR(40) NOT NULL,
    -- 화면에 보일 차례. 진입점을 맨 앞에 두는 식으로 출제자가 정한다.
    seq        INT         NOT NULL,
    -- 파일 이름. 경로는 쓸 수 없다 — 실행기가 거부한다 (#457).
    name       VARCHAR(60) NOT NULL,
    -- 그 파일의 시작 코드.
    template   TEXT        NOT NULL DEFAULT '',
    -- **고칠 수 있는 파일인가.** 거짓이면 제출에 실리지 않고 서버가 시작 코드를 그대로
    -- 쓴다 — "이 인터페이스는 건드리지 말고 구현만 하라" 를 표현하는 자리다.
    editable   BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_problem_files ON problem_files (problem_id, runtime_id, name);
CREATE INDEX idx_problem_files_problem ON problem_files (problem_id);

-- 제출이 실은 파일들 (#497).
--
-- **표로 나누지 않는다.** 제출은 많고 그중 대부분은 파일 하나인데, 표를 만들면 그
-- 경우에도 조인이 하나 는다. 파일은 언제나 통째로 읽고 통째로 쓴다.
--
-- 비어 있으면 파일 하나짜리 제출이고, 그때 진실은 `source_code` 다.
-- 차 있으면 **이쪽이 진실**이고 `source_code` 에는 진입점 파일의 내용이 함께 들어간다 —
-- 제출 목록·상세·통계처럼 소스를 하나로 보는 기존 경로가 그대로 돌게 하기 위함이다.
-- **`jsonb` 가 아니라 `text` 다.** 이 안을 질의할 일이 없다 — 파일은 언제나 통째로
-- 읽고 통째로 쓴다. `jsonb` 로 두면 얻는 것 없이 매핑에 형 변환이 하나 는다.
ALTER TABLE submissions ADD COLUMN source_files TEXT;
