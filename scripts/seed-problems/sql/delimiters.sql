-- 구분자가 값 안에 든 스키마 (#532).
--
-- **이 문제만 이 스키마를 쓴다.** 다른 SQL 문제가 함께 쓰는 library.sql 에 이런 값을
-- 섞으면, 그 문제들의 지문에 적힌 예시 데이터까지 바뀐다.
--
-- 채점이 값과 구분자를 가리지 못하면 여기서 드러난다 — 전에는 `-tA -F'|'` 로 내서
-- `a|b` 한 칸과 `a`,`b` 두 칸이 같은 줄이 됐다.

CREATE TABLE tracks (
    id     INT PRIMARY KEY,
    title  TEXT NOT NULL,
    artist TEXT NOT NULL,
    note   TEXT
);

INSERT INTO tracks (id, title, artist, note) VALUES
    -- 파이프: 옛 형식의 구분자였다.
    (1, 'Hello|World',  '가수 A',        '제목에 파이프'),
    -- 쉼표: 지금 형식(CSV)의 구분자다.
    (2, 'A, B, and C',  '가수 B',        '제목에 쉼표'),
    -- 따옴표: CSV 가 감싸는 데 쓰는 글자다.
    (3, 'She said "hi"', '가수 C',       '제목에 따옴표'),
    -- NULL 과 빈 글자는 다르다. 둘이 같아지면 채점이 틀린다.
    (4, '보통 제목',     '가수 D',        NULL),
    (5, '',             '가수 E|가수 F', '제목이 빈 글자');
