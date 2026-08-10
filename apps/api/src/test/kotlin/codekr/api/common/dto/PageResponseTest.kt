package codekr.api.common.dto

import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import kotlin.test.assertEquals

class PageResponseTest {

    @Test
    fun `Spring Page 를 페이지 응답으로 변환한다`() {
        val page = PageImpl(listOf("a", "b"), PageRequest.of(1, 2), 5)

        val response = PageResponse.from(page)

        assertEquals(listOf("a", "b"), response.content)
        assertEquals(1, response.page)
        assertEquals(2, response.size)
        assertEquals(5, response.totalElements)
        assertEquals(3, response.totalPages)
    }
}
