package codekr.api.runtime.controller

import codekr.api.config.security.PublicApi
import codekr.api.problem.entity.ProblemKind
import codekr.api.runtime.RuntimeDefinition
import codekr.api.runtime.RuntimeRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/runtimes")
class RuntimeController(private val runtimeRegistry: RuntimeRegistry) {

    /**
     * 실행 환경 목록. 문제 유형으로 거른다 (#60).
     *
     * 기본값이 stdin/stdout 인 이유: 이 인자가 없던 시절의 화면이 SQL 런타임을
     * 알고리즘 문제의 선택지로 보여주면 안 된다.
     */
    /**
     * 고를 수 있는 언어 갈래 (#618). 목록 필터가 이것으로 두 칸을 만든다.
     *
     * **유형으로 거르지 않는다.** 필터는 "무엇으로 풀 수 있나" 를 묻는 자리라 SQL·Redis
     * 까지 다 보여야 한다 — 유형을 아는 것은 서버이지 고르는 사람이 아니다.
     */
    @PublicApi
    @GetMapping("/languages")
    fun languages(): List<RuntimeLanguageResponse> =
        RuntimeLanguageResponse.from(runtimeRegistry.findAll())

    @PublicApi
    @GetMapping
    fun findAll(
        @RequestParam(defaultValue = "JUDGE_STDIO") problemKind: ProblemKind,
    ): List<RuntimeDefinition> = runtimeRegistry.findFor(problemKind)
}
