package codekr.api.board

import codekr.api.board.attachment.AttachmentService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * 한 글에 넣을 수 있는 이미지 장수 (#389).
 *
 * **크기 제한만으로는 안 막힌다** — 5MB 짜리를 백 장 넣으면 같은 결과이고, 읽는 사람의
 * 회선을 쓰는 것은 장수 쪽이다.
 *
 * 세는 규칙이 화면이 아니라 **서버**에 있어야 하는 이유: 화면이 세면 화면을 안 거치고
 * 보낸 요청에는 아무 제한이 없다.
 */
class AttachmentLimitTest {

    @Test
    fun `우리 저장소의 이미지만 센다`() {
        val body = """
            ![오류 화면](/api/v1/files/attachments/abc.jpg)
            ![남의 것](https://example.com/foo.png)
            ![아바타](/api/v1/files/avatars/def.png)
        """.trimIndent()

        // 남의 호스팅과 아바타는 이 제한의 대상이 아니다 — 우리 디스크를 쓰지 않는다.
        assertEquals(1, AttachmentService.countIn(body))
    }

    @Test
    fun `링크는 이미지가 아니다`() {
        // `[텍스트](주소)` 와 `![텍스트](주소)` 는 다르다. 앞엣것은 그림을 싣지 않는다.
        val body = "[첨부 보기](/api/v1/files/attachments/abc.jpg)"

        assertEquals(0, AttachmentService.countIn(body))
    }

    @Test
    fun `여러 장을 센다`() {
        val body = (1..12).joinToString("\n") { "![$it](/api/v1/files/attachments/$it.jpg)" }

        assertEquals(12, AttachmentService.countIn(body))
        // 상한을 넘겼다는 것이 이 값으로 판정된다.
        assertEquals(true, AttachmentService.countIn(body) > AttachmentService.PER_POST_LIMIT)
    }

    @Test
    fun `설명이 비어 있어도 센다`() {
        // `![](...)` 는 흔하다 — 편집기가 넣는 기본 형태이기도 하다.
        assertEquals(1, AttachmentService.countIn("![](/api/v1/files/attachments/a.jpg)"))
    }

    @Test
    fun `이미지가 없으면 0이다`() {
        assertEquals(0, AttachmentService.countIn("코드 블록만 있는 글\n\n```\nprint(1)\n```"))
    }
}
