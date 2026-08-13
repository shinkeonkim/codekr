-- 약관 동의 (#235).
--
-- **동의한 사실이 남지 않으면 동의를 받은 것이 아니다.** "언제, 어느 버전에" 동의했는지가
-- 없으면 나중에 확인할 수 없다.
--
-- 문서를 파일로만 두지 않는 이유가 그것이다 — 개정 이력이 git 에만 남으면 **누가 어느
-- 버전에 동의했는지**를 알 수 없다. 버전을 데이터로 두고 동의를 그 버전에 붙인다.

CREATE TABLE term_documents (
    id            BIGSERIAL PRIMARY KEY,
    -- SERVICE(이용약관) | PRIVACY(개인정보 처리방침)
    kind          VARCHAR(20) NOT NULL,
    -- 사람이 읽는 판 번호. "1.0" 처럼 쓴다.
    version       VARCHAR(20) NOT NULL,
    title         VARCHAR(200) NOT NULL,
    body          TEXT        NOT NULL,
    -- **시행일이 미래면 아직 받지 않는다.** 개정을 미리 넣어 두고 날짜에 맞춰 켜기 위해서다.
    effective_at  TIMESTAMPTZ NOT NULL,
    -- 필수와 선택을 나눈다. 지금은 둘 다 필수지만, 구조가 없으면 나중에 못 나눈다.
    required      BOOLEAN     NOT NULL DEFAULT true,
    /*
        **사소한 개정은 다시 받지 않는다** (#235).

        오타를 고칠 때마다 모든 회원을 막으면 개정 자체를 안 하게 된다. 다시 받아야 하는
        개정에만 이 값을 켠다 — 그 판단은 사람이 한다.
    */
    reconsent     BOOLEAN     NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (kind, version)
);

CREATE INDEX idx_term_documents_kind ON term_documents (kind, effective_at DESC);

CREATE TABLE term_agreements (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- **버전에 붙인다.** 종류에만 붙이면 개정 뒤에 "무엇에 동의했는지" 가 사라진다.
    document_id BIGINT     NOT NULL REFERENCES term_documents (id),
    agreed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, document_id)
);

CREATE INDEX idx_term_agreements_user ON term_agreements (user_id);

-- 최소 초안 (#235).
--
-- **빈 약관으로 동의를 받는 것은 무의미하다.** 법적 효력이 있는 문안을 다듬는 것은 다른
-- 일이지만, 지금까지 코드가 이미 정해 둔 것들(탈퇴 시 삭제 #140, 제출 코드 공개 범위 #33,
-- 보관 기간 ADR-0007)은 적어 둘 수 있다. V25 마이그레이션이 "여기서 정한 것이 곧
-- 개인정보 처리 방침이 된다" 고 적어 두었다.
INSERT INTO term_documents (kind, version, title, body, effective_at, required) VALUES
('SERVICE', '1.0', '서비스 이용약관',
'제1조 (목적)
이 약관은 코드.kr(이하 "서비스")의 이용 조건을 정합니다.

제2조 (계정)
- 가입에는 이메일 주소와 닉네임이 필요합니다.
- 한 사람이 여러 계정을 만드는 것을 제한할 수 있습니다.

제3조 (이용자가 올린 것)
- 문제 풀이, 게시글, 댓글의 저작권은 올린 사람에게 있습니다.
- 서비스는 그것을 서비스 안에서 보여주기 위해 사용합니다.
- 제출한 코드의 공개 범위는 이용자가 정합니다(기본값은 설정에서 바꿀 수 있습니다).

제4조 (제한)
- 다른 이용자를 괴롭히거나 채점 시스템을 방해하는 행위는 제한됩니다.
- 제한은 글쓰기 또는 제출을 일정 기간 막는 형태이며, 사유와 기간을 알립니다.

제5조 (탈퇴)
- 언제든 탈퇴할 수 있고, 탈퇴하면 이메일·닉네임 등 식별 정보는 즉시 삭제됩니다.
- 이미 올린 글과 댓글은 남으며 작성자는 "탈퇴한 사용자" 로 표시됩니다.',
 now(), true),
('PRIVACY', '1.0', '개인정보 처리방침',
'1. 수집하는 항목
- 필수: 이메일 주소, 비밀번호(해시), 닉네임
- 자동 수집: 제출 기록, 활동 기록

2. 이용 목적
- 계정 식별과 로그인
- 채점 결과와 랭킹 제공
- 공지·알림 전달

3. 보관 기간
- 계정 정보: 탈퇴 시까지. 탈퇴하면 이메일·닉네임은 즉시 익명 값으로 대체됩니다.
- 제출 기록: 문제 통계의 원자료이므로 익명화된 상태로 남습니다.
- 백업본: 최대 1일 주기로 보관되며 순차적으로 폐기됩니다.

4. 제3자 제공
- 제공하지 않습니다. 메일 발송을 위한 처리 위탁이 있을 수 있으며, 그 경우 이 문서에
  위탁 대상을 밝힙니다.

5. 이용자의 권리
- 자신의 정보를 열람·수정할 수 있고, 언제든 탈퇴할 수 있습니다.',
 now(), true);
