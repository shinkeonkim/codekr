package codekr.api.auth

import codekr.api.support.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AuthIntegrationTest : IntegrationTestBase() {

    @Test
    fun `가입한 사용자가 토큰으로 내 정보를 조회한다`() {
        val accessToken = readToken(signup("member@codekr.dev", "코더원"))

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("member@codekr.dev"))
            .andExpect(jsonPath("$.role").value("USER"))
    }

    @Test
    fun `같은 이메일로 두 번 가입할 수 없다`() {
        signup("dup@codekr.dev", "중복유저")

        mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"dup@codekr.dev","password":"password123","nickname":"다른닉"}"""),
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
    }

    @Test
    fun `토큰 없이 보호된 엔드포인트에 접근하면 401 이다`() {
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized)
    }

    private fun signup(email: String, nickname: String): MvcResult =
        mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"password123","nickname":"$nickname"}"""),
        ).andExpect(status().isCreated).andReturn()

    private fun readToken(result: MvcResult): String =
        Regex("\"accessToken\":\"([^\"]+)\"").find(result.response.contentAsString)!!.groupValues[1]
}
