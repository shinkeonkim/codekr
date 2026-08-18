-- MariaDB 판 도서관 스키마 (#454).
--
-- **같은 스키마를 두 벌 둔다.** SQL 은 방언이 있어서 한 벌로는 되지 않는다 —
-- `TEXT` 는 MariaDB 에서 인덱스·기본값 규칙이 다르고(그래서 `VARCHAR`), 날짜 리터럴의
-- `DATE '…'` 표기도 MariaDB 는 받지 않는다(`'…'` 로 충분하다).
--
-- 문제 하나에 DB 하나이므로(#454) 스키마도 문제를 따라 갈라진다. 그것이 "SQL 을
-- 배웠다" 와 "PostgreSQL 을 배웠다" 를 가르는 값이다.

CREATE TABLE members (
    id        INT PRIMARY KEY,
    name      VARCHAR(40) NOT NULL,
    city      VARCHAR(40) NOT NULL,
    joined_on DATE NOT NULL
);

CREATE TABLE books (
    id    INT PRIMARY KEY,
    title VARCHAR(40) NOT NULL,
    genre VARCHAR(40) NOT NULL,
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
    (1, '김서준', '서울', '2024-03-02'),
    (2, '이하윤', '부산', '2024-05-17'),
    (3, '박도윤', '서울', '2025-01-09'),
    (4, '최지우', '대구', '2025-02-20'),
    (5, '정예린', '서울', '2025-06-11');

INSERT INTO books (id, title, genre, price) VALUES
    (1, '자료구조 입문',   '전공', 28000),
    (2, '알고리즘 산책',   '전공', 35000),
    (3, '바다의 기억',     '소설', 14000),
    (4, '고양이 요리사',   '소설', 12000),
    (5, '통계로 보는 세상', '교양', 19000),
    (6, '우주의 하루',     '교양', 22000),
    (7, '숫자의 감각',     '교양', 16000);

INSERT INTO loans (id, member_id, book_id, loaned_on, returned_on) VALUES
    (1, 1, 1, '2025-07-01', '2025-07-10'),
    (2, 1, 3, '2025-07-05', NULL),
    (3, 2, 2, '2025-07-02', '2025-07-20'),
    (4, 3, 1, '2025-07-08', NULL),
    (5, 3, 4, '2025-07-09', '2025-07-15'),
    (6, 3, 5, '2025-07-11', NULL),
    (7, 4, 6, '2025-07-12', '2025-07-19'),
    (8, 5, 2, '2025-07-14', NULL),
    (9, 1, 6, '2025-07-20', NULL);
