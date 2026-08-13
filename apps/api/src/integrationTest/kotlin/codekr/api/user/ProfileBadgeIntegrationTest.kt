package codekr.api.user

import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 프로필 배지 (#475).
 *
 * **사용자가 스스로 거는 링크**다. README 에 박히므로 로그인 없이 열려야 하고,
 * 깨진 이미지가 보이면 그 사람의 잘못이 아니라 우리 고장으로 보인다.
 */
class ProfileBadgeIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var redisTemplate: StringRedisTemplate

    @BeforeEach
    fun setUp() {
        userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER)))
        // 요청 수는 Redis 에 쌓인다 (#475). 시험끼리 창을 나눠 쓰면 앞 시험이 뒤 시험의
        // 몫을 먹는다 — 이 시험들은 같은 주소(127.0.0.1)에서 오기 때문이다.
        redisTemplate.keys("codekr:badge:rate:*").forEach(redisTemplate::delete)
    }

    @Test
    fun `로그인 없이 배지를 받는다`() {
        val handle = userRepository.findByNickname("풀이왕")!!.handle

        mockMvc.perform(get("/badge/$handle.svg"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
            .andExpect(header().string("X-Robots-Tag", "noindex"))
    }

    @Test
    fun `캐시를 열어 준다`() {
        /*
          **캐시가 이 기능의 전부다.** 짧으면 README 를 여는 사람 수만큼 우리 서버가 맞고,
          길면 숫자가 며칠 전 것으로 남는다.
        */
        val handle = userRepository.findByNickname("풀이왕")!!.handle

        val cacheControl = mockMvc.perform(get("/badge/$handle.svg"))
            .andExpect(status().isOk)
            .andReturn().response.getHeader("Cache-Control")

        assert(cacheControl?.contains("max-age=600") == true) { "10분 캐시가 아니다: $cacheControl" }
        assert(cacheControl?.contains("stale-while-revalidate") == true) {
            "다시 받아오는 동안 옛 그림을 보여 줘야 한다: $cacheControl"
        }
    }

    @Test
    fun `없는 사람도 깨진 이미지가 아니라 배지를 받는다`() {
        // README 에서 깨진 이미지는 그 사람의 잘못이 아니라 **우리 고장**으로 보인다.
        mockMvc.perform(get("/badge/nobody-here.svg"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("없는 사용자")))
    }

    @Test
    fun `탈퇴한 사람은 없는 사람과 같은 그림이다`() {
        // 다르게 주면 "이 handle 은 있었다" 가 새어 나간다 (#140).
        val user = userRepository.findByNickname("풀이왕")!!
        val handle = user.handle
        user.withdraw()
        userRepository.save(user)

        mockMvc.perform(get("/badge/$handle.svg"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("없는 사용자")))
    }

    @Test
    fun `어두운 배경용 배지를 고를 수 있다`() {
        // <img> 로 박힌 SVG 에는 보는 쪽의 설정이 닿지 않는다 — 주소로 고르게 한다.
        val handle = userRepository.findByNickname("풀이왕")!!.handle

        mockMvc.perform(get("/badge/$handle.svg").param("theme", "dark"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("#0f172a")))
    }

    @Test
    fun `한 곳이 계속 두드리면 막는다`() {
        /*
          **로그인이 없다.** 배지는 README 안에서 열려야 하므로 토큰을 요구할 수 없고,
          그러면 누구나 무제한으로 부를 수 있는 자리가 된다.

          첫 방어선은 캐시다 — 여기까지 오는 것은 그것을 지나온 요청이다.
        */
        val handle = userRepository.findByNickname("풀이왕")!!.handle
        repeat(120) { mockMvc.perform(get("/badge/$handle.svg")).andExpect(status().isOk) }

        mockMvc.perform(get("/badge/$handle.svg")).andExpect(status().isTooManyRequests)
    }

    @Test
    fun `밖의 것을 하나도 참조하지 않는다`() {
        /*
          GitHub 은 이미지를 자기 프록시로 받아 보여 준다. 그 안에서는 웹폰트도 다른
          이미지도 따라오지 않는다 — 참조가 있으면 **거기서만** 깨진다.
        */
        val handle = userRepository.findByNickname("풀이왕")!!.handle

        val svg = mockMvc.perform(get("/badge/$handle.svg"))
            .andReturn().response.getContentAsString(Charsets.UTF_8)

        // `xmlns` 의 http 주소는 이름표일 뿐 받아오는 것이 아니다 — 그것 말고를 본다.
        for (forbidden in listOf("https://", "@import", "<image", "xlink:href", "<script")) {
            assert(!svg.contains(forbidden)) { "밖의 것을 참조한다($forbidden): $svg" }
        }
    }
}
