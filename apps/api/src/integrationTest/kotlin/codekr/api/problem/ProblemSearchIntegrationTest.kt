package codekr.api.problem

import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.entity.ProblemTemplate
import codekr.api.problem.entity.ProblemTestcase
import codekr.api.problem.entity.TestcaseVisibility
import codekr.api.problem.repository.ProblemRepository
import codekr.api.support.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals

class ProblemSearchIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var problemRepository: ProblemRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @BeforeEach
    fun setUp() {
        problemRepository.saveAll(
            listOf(
                newProblem("two-sum", "두 수의 합", ProblemCategory.ALGORITHM, Difficulty.BRONZE_5, published = true),
                newProblem("join-basics", "조인 기초", ProblemCategory.SQL, Difficulty.GOLD_4, published = true),
                newProblem("draft", "미공개 문제", ProblemCategory.ALGORITHM, Difficulty.RUBY_1, published = false),
            ),
        )
    }

    @Test
    fun `공개된 문제만 목록에 노출된다`() {
        mockMvc.perform(get("/api/v1/problems"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    @Test
    fun `카테고리와 키워드로 필터링한다`() {
        mockMvc.perform(get("/api/v1/problems").param("category", "SQL"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].slug").value("join-basics"))

        mockMvc.perform(get("/api/v1/problems").param("q", "두 수"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `티어로 필터링하고 난이도 순으로 정렬한다`() {
        mockMvc.perform(get("/api/v1/problems").param("tier", "GOLD"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].slug").value("join-basics"))
            .andExpect(jsonPath("$.content[0].difficulty").value("GOLD_4"))
            .andExpect(jsonPath("$.content[0].difficultyLabel").value("골드 4"))

        mockMvc.perform(get("/api/v1/problems").param("sort", "DIFFICULTY"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].slug").value("two-sum"))
    }

    @Test
    fun `상세 조회는 히든 테스트케이스를 노출하지 않는다`() {
        // 지연 로딩 컬렉션을 다루므로 트랜잭션 안에서 준비한다.
        transactionTemplate.executeWithoutResult {
            problemRepository.findBySlugAndDeletedAtIsNull("two-sum")!!.addTestcases(
                listOf(
                    ProblemTestcase(1, "1 2\n", "3\n", TestcaseVisibility.PUBLIC),
                    ProblemTestcase(2, "비밀 입력\n", "비밀 출력\n", TestcaseVisibility.HIDDEN),
                ),
            )
        }

        mockMvc.perform(get("/api/v1/problems/two-sum"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.examples.length()").value(1))
            .andExpect(jsonPath("$.examples[0].input").value("1 2\n"))
            // 런타임 개수는 정의 파일이 늘어나면 바뀐다 — 개수가 아니라 존재를 확인한다.
            .andExpect(jsonPath("$.runtimes[?(@.id == 'python:3.12')]").exists())
    }

    @Test
    fun `문제가 지정한 초기 코드가 런타임 기본 템플릿을 대체한다`() {
        transactionTemplate.executeWithoutResult {
            problemRepository.findBySlugAndDeletedAtIsNull("two-sum")!!.addTemplates(
                listOf(ProblemTemplate("python:3.12", "# 이 문제 전용 시작 코드\n")),
            )
        }

        mockMvc.perform(get("/api/v1/problems/two-sum"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runtimes[?(@.id == 'python:3.12')].template").value("# 이 문제 전용 시작 코드\n"))
            // 지정하지 않은 런타임은 레지스트리 기본 템플릿을 그대로 쓴다.
            .andExpect(jsonPath("$.runtimes[?(@.id == 'cpp:17')].template").isNotEmpty)
    }

    @Test
    fun `미공개 문제 상세는 404 다`() {
        mockMvc.perform(get("/api/v1/problems/draft")).andExpect(status().isNotFound)
    }

    @Test
    fun `소프트 삭제된 문제는 목록과 상세에서 사라진다`() {
        transactionTemplate.executeWithoutResult {
            problemRepository.findBySlugAndDeletedAtIsNull("two-sum")!!.delete()
        }

        mockMvc.perform(get("/api/v1/problems"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
        mockMvc.perform(get("/api/v1/problems/two-sum")).andExpect(status().isNotFound)
    }

    private fun newProblem(
        slug: String,
        title: String,
        category: ProblemCategory,
        difficulty: Difficulty,
        published: Boolean,
    ) = Problem(
        slug = slug,
        title = title,
        category = category,
        difficultyLevel = difficulty.level,
        description = "설명",
        published = published,
    )

    @Test
    fun `정렬 기준마다 순서가 달라진다`() {
        // 웹이 이 파라미터를 보내지 않아 목록이 늘 최신순이었다 (#132).
        mockMvc.perform(get("/api/v1/problems").param("sort", "DIFFICULTY"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].difficultyLevel").value(1))

        mockMvc.perform(get("/api/v1/problems").param("sort", "TITLE"))
            .andExpect(status().isOk)

        // 기본은 최신순이다 — 인자를 보내지 않던 화면이 그대로 돌아야 한다.
        val latest = mockMvc.perform(get("/api/v1/problems"))
            .andReturn().response.contentAsString
        val explicit = mockMvc.perform(get("/api/v1/problems").param("sort", "LATEST"))
            .andReturn().response.contentAsString
        assertEquals(latest, explicit)
    }

    @Test
    fun `알 수 없는 정렬 기준은 거부한다`() {
        // 조용히 최신순으로 넘어가면 사용자는 정렬이 안 먹는다고 느낀다.
        mockMvc.perform(get("/api/v1/problems").param("sort", "POPULAR"))
            .andExpect(status().isBadRequest)
    }
}
