package codekr.api.support

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 요청 잘못이 500 으로 보이지 않게 (#324).
 *
 * **500 은 "우리 잘못" 이고 404 는 "그런 것이 없다" 다.** 오타 하나가 서버 오류로
 * 잡히면 경보가 울리고 진짜 장애가 그 사이에 묻힌다. #132 가 잘못된 질의 인자에서
 * 같은 판단을 했다.
 *
 * 이 시험이 지키는 것은 **상태 코드의 뜻**이다 — 오류 처리를 다시 만지는 사람이
 * 조용히 500 으로 되돌리지 못하게 한다.
 */
class ErrorMappingIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Test
    fun `없는 경로는 404 다`() {
        mockMvc.perform(delete("/api/v1/admin/audit-logs/1").header("Authorization", "Bearer ${adminToken()}"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
    }

    @Test
    fun `비로그인에게는 인증이 먼저 걸린다`() {
        /*
          없는 경로를 404 로 알리는 것이 정보 노출인지 — **인증 필터가 먼저 걸린다.**
          비로그인은 그 경로가 있는지조차 알 수 없다.
        */
        mockMvc.perform(delete("/api/v1/admin/audit-logs/1"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `못 쓰는 메서드는 405 이고 쓸 수 있는 것을 알려 준다`() {
        mockMvc.perform(post("/api/v1/users/me").header("Authorization", "Bearer ${adminToken()}"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
            // 무엇을 써야 하는지 모르면 고칠 수 없다.
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("GET")))
    }

    @Test
    fun `읽을 수 없는 본문은 400 이다`() {
        mockMvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""{"email":"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
    }

    @Test
    fun `파서의 말이 그대로 새어 나가지 않는다`() {
        /*
          줄·열 번호와 클래스 이름이 딸려 오는데 그것은 우리 내부 구조다.
          응답에는 사람이 읽을 문구만 남긴다.
        */
        mockMvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""{"email":"""),
        )
            .andExpect(jsonPath("$.message").value("요청 본문을 읽을 수 없습니다."))
    }

    @Test
    fun `모르는 형식은 415 다`() {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("email=x"))
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
    }

    @Test
    fun `잘못된 enum 인자는 어떤 값이 되는지 알려 준다`() {
        /*
          #132 가 만든 안내다. **그 문구가 지금까지 깨진 채 나가고 있었다** —
          문자열 보간이 이스케이프되어 `${'$'}{e.name} 은 ...` 이 그대로 보였다.
          형태만 단언하면 이런 것을 못 잡으므로 **내용**을 본다.
        */
        mockMvc.perform(get("/api/v1/problems?sort=NOPE"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("sort")))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("e.name"))))
    }

    @Test
    fun `있는 경로는 그대로 돈다`() {
        // 오류 처리를 넓히다가 정상 경로까지 삼키지 않았는지 — 이것이 없으면
        // 위 시험들은 "전부 404 로 만들어도" 통과한다.
        mockMvc.perform(get("/api/v1/problems")).andExpect(status().isOk)
    }

    private fun adminToken(): String {
        val admin = userRepository.findByEmail("admin@codekr.dev")
            ?: run {
                mockMvc.perform(
                    post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("admin@codekr.dev", "password1234", "오류시험")),
                ).andExpect(status().isCreated)
                userRepository.findByEmail("admin@codekr.dev")!!
            }
        return tokenProvider.issueAccessToken(admin)
    }
}
