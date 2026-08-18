-- SQL 시드 문제 다섯 개가 함께 쓰는 스키마 (#313).
--
-- **이 사이트와 무관한 주제다.** 문제·제출·사용자 같은 표를 쓰면 친숙해 보이지만,
-- 실제 스키마와 다를 때 오히려 헷갈린다.
--
-- **행 수를 적게 둔다.** 푸는 사람이 머릿속에 담을 수 있어야 하고, 채점할 때마다
-- 컨테이너 안에서 새로 주입된다.
--
-- 다섯 문제가 같은 스키마를 쓴다 — 표 구조를 한 번만 익히면 되고 문제 사이에 흐름이
-- 생긴다. `schema_sql` 은 문제마다 저장되므로 여기서 한 벌만 두고 시드가 끼워 넣는다.

CREATE TABLE members (
    id        INT PRIMARY KEY,
    name      TEXT NOT NULL,
    city      TEXT NOT NULL,
    joined_on DATE NOT NULL
);

CREATE TABLE books (
    id    INT PRIMARY KEY,
    title TEXT NOT NULL,
    genre TEXT NOT NULL,
    price INT  NOT NULL
);

CREATE TABLE loans (
    id          INT PRIMARY KEY,
    member_id   INT  NOT NULL REFERENCES members (id),
    book_id     INT  NOT NULL REFERENCES books (id),
    loaned_on   DATE NOT NULL,
    -- 아직 반납하지 않았으면 NULL 이다.
    returned_on DATE
);

INSERT INTO members (id, name, city, joined_on) VALUES
    (1, '김서준', '서울', DATE '2024-03-02'),
    (2, '이하윤', '부산', DATE '2024-05-17'),
    (3, '박도윤', '서울', DATE '2025-01-09'),
    (4, '최지우', '대구', DATE '2025-02-20'),
    (5, '정예린', '서울', DATE '2025-06-11');

INSERT INTO books (id, title, genre, price) VALUES
    (1, '자료구조 입문',   '전공', 28000),
    (2, '알고리즘 산책',   '전공', 35000),
    (3, '바다의 기억',     '소설', 14000),
    (4, '고양이 요리사',   '소설', 12000),
    (5, '통계로 보는 세상', '교양', 19000),
    (6, '우주의 하루',     '교양', 22000),
    (7, '숫자의 감각',     '교양', 16000);

INSERT INTO loans (id, member_id, book_id, loaned_on, returned_on) VALUES
    (1, 1, 1, DATE '2025-07-01', DATE '2025-07-10'),
    (2, 1, 3, DATE '2025-07-05', NULL),
    (3, 2, 2, DATE '2025-07-02', DATE '2025-07-20'),
    (4, 3, 1, DATE '2025-07-08', NULL),
    (5, 3, 4, DATE '2025-07-09', DATE '2025-07-15'),
    (6, 3, 5, DATE '2025-07-11', NULL),
    (7, 4, 6, DATE '2025-07-12', DATE '2025-07-19'),
    (8, 5, 2, DATE '2025-07-14', NULL),
    (9, 1, 6, DATE '2025-07-20', NULL);
