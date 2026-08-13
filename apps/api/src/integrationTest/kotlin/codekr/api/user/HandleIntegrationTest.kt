package codekr.api.user

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 부르는 이름과 가리키는 주소를 나눈다 (#307).
 *
 * **이름 하나가 표시 이름이자 주소였다.** 그래서 이름을 바꾸면 링크가 끊기고,
 * 끊기지 않게 하려니 이름을 아예 바꿀 수 없었다. #204 가 문제에서 같은 것을 나눴다.
 */
class HandleIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Test
    fun `가입할 때 주소를 직접 정한다`() {
        signup("me@codekr.dev", "코더한글", handle = "coder-kim")

        mockMvc.perform(get("/api/v1/users/coder-kim").header("Authorization", "Bearer ${token("me@codekr.dev")}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nickname").value("코더한글"))
            .andExpect(jsonPath("$.handle").value("coder-kim"))
    }

    @Test
    fun `안 정하면 이름에서 만든다`() {
        signup("auto@codekr.dev", "AutoCoder")

        // 소문자·숫자·하이픈만 남긴다.
        kotlin.test.assertEquals("autocoder", userRepository.findByEmail("auto@codekr.dev")!!.handle)
    }

    @Test
    fun `한글 이름이면 임의로 만든다`() {
        // 한글은 그대로 주소가 될 수 없다 — 남는 것이 없으면 부르는 쪽이 정한다.
        signup("kr@codekr.dev", "한글이름")

        val handle = userRepository.findByEmail("kr@codekr.dev")!!.handle
        kotlin.test.assertTrue(handle.isNotBlank(), "빈 주소로 저장되면 안 된다")
        kotlin.test.assertTrue(Regex("^[a-z0-9][a-z0-9-]+$").matches(handle), "만들어진 주소도 규칙을 지켜야 한다: $handle")
    }

    @Test
    fun `이미 쓰는 주소면 그렇게 알린다`() {
        signup("first@codekr.dev", "첫사람", handle = "taken")

        // **500 이 아니다** — 유니크 제약 위반을 그대로 흘리지 않는다.
        mockMvc.perform(
            post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(signupBody("second@codekr.dev", "password1234", "둘째사람").dropLast(1) + ""","handle":"taken"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `규칙에 맞지 않는 주소는 막는다`() {
        mockMvc.perform(
            post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(signupBody("bad@codekr.dev", "password1234", "이름").dropLast(1) + ""","handle":"대문자AND한글"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `이름을 바꿔도 주소는 그대로다`() {
        signup("rename@codekr.dev", "옛이름", handle = "stable-one")
        val token = token("rename@codekr.dev")

        mockMvc.perform(
            patch("/api/v1/users/me/profile").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"displayName":"새이름"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("새이름"))
            .andExpect(jsonPath("$.handle").value("stable-one"))

        // **주소가 그대로라 링크가 살아 있다.**
        mockMvc.perform(get("/api/v1/users/stable-one").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nickname").value("새이름"))
    }

    @Test
    fun `이미 쓰는 이름으로는 못 바꾼다`() {
        signup("a@codekr.dev", "가진이름", handle = "one-user")
        signup("b@codekr.dev", "다른이름", handle = "two-user")

        mockMvc.perform(
            patch("/api/v1/users/me/profile").header("Authorization", "Bearer ${token("b@codekr.dev")}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"displayName":"가진이름"}"""),
        ).andExpect(status().isConflict)
    }

    @Test
    fun `옛 주소로 들어와도 프로필이 열린다`() {
        // 링크를 이미 주고받은 사람에게 404 를 보이는 것보다 낫다 — 다만 이름은
        // 바뀌므로 그 길은 언젠가 끊긴다.
        signup("old@codekr.dev", "옛주소사람", handle = "old-path")

        mockMvc.perform(
            get("/api/v1/users/옛주소사람").header("Authorization", "Bearer ${token("old@codekr.dev")}"),
        ).andExpect(status().isOk)
    }

    private fun signup(email: String, nickname: String, handle: String? = null) {
        val body = signupBody(email, "password1234", nickname)
        val withHandle = if (handle == null) body else body.dropLast(1) + ""","handle":"$handle"}"""
        mockMvc.perform(
            post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(withHandle),
        ).andExpect(status().isCreated)
    }

    private fun token(email: String): String =
        tokenProvider.issueAccessToken(userRepository.findByEmail(email)!!)
}
