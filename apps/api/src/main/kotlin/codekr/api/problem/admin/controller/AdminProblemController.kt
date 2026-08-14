package codekr.api.problem.admin.controller

import codekr.api.user.entity.UserRole
import codekr.api.config.security.AdminApi
import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.dto.PageResponse
import codekr.api.problem.admin.dto.AdminProblemDetailResponse
import codekr.api.problem.admin.dto.ProblemCreatedResponse
import codekr.api.problem.admin.dto.ProblemImportPreview
import codekr.api.problem.admin.dto.ProblemUpsertRequest
import codekr.api.problem.admin.dto.VerificationResponse
import codekr.api.problem.admin.service.AdminProblemService
import codekr.api.problem.admin.service.ProblemImportService
import codekr.api.problem.admin.service.SolutionVerificationService
import codekr.api.problem.dto.ProblemSummaryResponse
import codekr.api.problem.entity.DifficultyTier
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.repository.ProblemSearchCondition
import codekr.api.problem.repository.ProblemSort
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

private const val MAX_PAGE_SIZE = 100

/** 접근 제어는 SecurityConfig 의 admin 경로 규칙(hasRole("ADMIN"))이 담당한다. */
@RestController
@RequestMapping("/api/v1/admin/problems")
class AdminProblemController(
    private val adminProblemService: AdminProblemService,
    private val verificationService: SolutionVerificationService,
    private val importService: ProblemImportService,
) {

    /**
     * 묶음을 읽어 **무엇이 들어올지만** 돌려준다 (#537).
     *
     * **아무것도 만들지 않는다.** 파일을 고르자마자 만들어 버리면 잘못 만든 묶음이
     * 문제 번호를 하나 먹고 지워야 할 것으로 남는다 — 번호는 사용자에게 보이는 값이다 (#204).
     *
     * `dryRun` 플래그로 [importArchive] 안에서 갈리게 하지 않았다. 성공 응답의 모양이
     * 둘로 갈리는 API 가 되기 때문이다.
     *
     * **미리보기 뒤 저장은 파일을 다시 올린다.** 서버가 올린 것을 들고 있지 않는다 —
     * "원본이 남지 않는다"(#479)를 지키려면 그래야 한다.
     */
    @AdminApi(UserRole.PROBLEM_SETTER)
    @PostMapping("/imports/preview", consumes = ["multipart/form-data"])
    fun previewArchive(@RequestPart("file") file: MultipartFile): ProblemImportPreview =
        importService.preview(file)

    /**
     * 묶음 파일로 문제를 만든다 (#479, #537).
     *
     * 테스트케이스가 백 개를 넘으면 폼으로는 못 만든다. 묶음은 `problem.json` +
     * testcases 아래의 `{seq}.in`·`{seq}.out` 이고, **JSON 은 시드와 같은
     * 형식**이다 — 형식이 둘이면 어느 쪽이 진짜인지 알 수 없게 된다.
     *
     * **맨 JSON 도 받는다** (#537). 시드 18개와 스킬(#480~#482)이 내놓는 것이 전부
     * 맨 JSON 이라, 테스트케이스가 세 개인 문제까지 압축하게 할 이유가 없다.
     * zip 인지 JSON 인지는 **매직 바이트**로 가른다 — 이름과 `Content-Type` 은 믿지 않는다.
     *
     * **언제나 초안으로 들어온다.** 올린 것이 바로 공개되면 잘못 만든 묶음이 그대로
     * 사람들 앞에 놓이고, 무엇이 들어왔는지 보기 전에 되돌릴 방법이 없다.
     */
    @AdminApi(UserRole.PROBLEM_SETTER)
    @PostMapping("/imports", consumes = ["multipart/form-data"])
    @ResponseStatus(HttpStatus.CREATED)
    fun importArchive(
        @RequestPart("file") file: MultipartFile,
        principal: AuthPrincipal,
    ): ProblemCreatedResponse = importService.import(file, principal.userId)

    @AdminApi(UserRole.PROBLEM_SETTER)
    @GetMapping
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) category: ProblemCategory?,
        @RequestParam(required = false) tier: DifficultyTier?,
        @RequestParam(defaultValue = "LATEST") sort: ProblemSort,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<ProblemSummaryResponse> {
        // published = null → 미공개 문제까지 포함한다.
        val condition = ProblemSearchCondition(q, category, tier, sort = sort, published = null)
        return adminProblemService.search(condition, PageRequest.of(maxOf(page, 0), size.coerceIn(1, MAX_PAGE_SIZE)))
    }

    @AdminApi(UserRole.PROBLEM_SETTER)
    @GetMapping("/{id}")
    fun findOne(@PathVariable id: Long): AdminProblemDetailResponse = adminProblemService.findDetail(id)

    @AdminApi(UserRole.PROBLEM_SETTER)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: ProblemUpsertRequest,
        principal: AuthPrincipal,
    ): ProblemCreatedResponse = adminProblemService.create(request, principal.userId)

    @AdminApi(UserRole.PROBLEM_SETTER)
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: ProblemUpsertRequest,
    ): AdminProblemDetailResponse = adminProblemService.update(id, request)

    /**
     * 등록한 정답 코드로 전체 테스트케이스를 검증한다.
     * 진행 상황은 문제 상세의 `verification` 으로 확인한다 (사용자 제출과 같은 채점 큐를 쓴다).
     */
    @AdminApi(UserRole.PROBLEM_SETTER)
    @PostMapping("/{id}/verify")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun verify(@PathVariable id: Long, principal: AuthPrincipal): VerificationResponse =
        verificationService.verify(id, principal.userId)

    @AdminApi(UserRole.PROBLEM_SETTER)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = adminProblemService.delete(id)
}
