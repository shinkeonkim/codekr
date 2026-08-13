-- 글에 붙는 이미지 (#389).
--
-- **표가 필요한 이유는 정리 때문이다.** 오브젝트 스토리지에는 "누가 언제 올렸는지" 가
-- 없다. 올려 놓고 글을 안 쓰면 주인 없는 파일이 남는데, 그것을 치우려면 나이와 참조
-- 여부를 알아야 한다 (#46 의 정리 배치가 같은 일을 한다).
CREATE TABLE post_attachments (
    id          BIGSERIAL PRIMARY KEY,
    uploader_id BIGINT       NOT NULL REFERENCES users (id),
    -- 오브젝트 스토리지의 키. 주소는 여기서 만들지 않는다 — 만드는 곳이 하나여야 한다.
    storage_key VARCHAR(200) NOT NULL,
    byte_size   INTEGER      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 같은 이미지를 두 번 올리면 키가 같다(내용 해시). 행도 하나면 된다.
CREATE UNIQUE INDEX uq_post_attachments_key ON post_attachments (storage_key);

-- 정리 배치가 "오래됐고 아무 글에도 안 쓰인 것" 을 찾을 때 쓴다.
CREATE INDEX idx_post_attachments_created ON post_attachments (created_at);

-- 한 사람이 얼마나 올렸는지 세는 데 쓴다.
CREATE INDEX idx_post_attachments_uploader ON post_attachments (uploader_id, created_at DESC);
