package codekr.api.runtime.controller

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
    @GetMapping
    fun findAll(
        @RequestParam(defaultValue = "JUDGE_STDIO") problemKind: ProblemKind,
    ): List<RuntimeDefinition> = runtimeRegistry.findFor(problemKind)
}
