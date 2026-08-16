package codekr.api.problem.admin.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.admin.dto.ProblemCreatedResponse
import codekr.api.problem.admin.dto.ProblemImportPreview
import codekr.api.problem.admin.dto.ProblemUpsertRequest
import codekr.api.problem.admin.dto.TestcaseRequest
import jakarta.validation.Validator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

/**
 * 묶음 파일로 문제를 만든다 (#479, #537).
 *
 * **테스트케이스가 백 개를 넘으면 폼으로는 못 만든다.** 시드 문제는 이미 JSON 으로
 * 만들고 있는데(`scripts/seed-problems` 의 json 파일) 어드민이 그것을 쓸 수 없었다 —
 * 형식은 있는데 길이 없었다.
 *
 * ## 언제나 **초안**으로 들어온다
 *
 * 올린 것이 바로 공개되면, 잘못 만든 묶음이 그대로 사람들 앞에 놓인다. 그리고 무엇이
 * 들어왔는지 보기 전에 되돌릴 방법이 없다. `published` 는 묶음이 무엇이라 적었든
 * **거짓으로 덮는다.**
 *
 * ## 동기로 처리한다
 *
 * 큐에 넣는 길(Redis 가 이미 있다)을 보지 않았다. 그러면 **진행 상태를 보여줄 자리**가
 * 필요하고, 그 자리는 이 기능이 실제로 느린지 본 뒤에 만드는 것이 맞다. 상한이
 * 64MB·5000 파일이므로 한 요청 안에서 끝난다 — 넘으면 그 전에 거절된다.
 */
@Service
class ProblemImportService(
    private val adminProblemService: AdminProblemService,
    private val objectMapper: ObjectMapper,
    private val validator: Validator,
) {

    private companion object {
        /** 시드가 쓰던 키 그대로다 (#313). 서버는 이것을 sqlSpec.schemaSql 로 푼다. */
        const val SCHEMA_FILE_KEY = "sqlSchemaFile"
    }

    @Transactional
    fun import(file: MultipartFile, createdBy: Long): ProblemCreatedResponse {
        val read = read(file)

        // 폼으로 만들 때와 **같은 규칙**을 지난다 (#59, #60, #454, #455).
        // 여기서만 통과하는 길을 두면 그 길로 들어온 문제가 화면에서 고쳐지지 않는다.
        val violations = violationsOf(read.request)
        if (violations.isNotEmpty()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, violations.joinToString())
        }
        return adminProblemService.create(read.request, createdBy)
    }

    /**
     * 읽기만 하고 **아무것도 만들지 않는다** (#537).
     *
     * `import` 와 **같은 [read] 를 지난다.** 여기서만 통과하는 길이 생기면
     * "미리보기는 됐는데 저장이 실패" 가 나고, 그러면 미리보기를 믿을 수 없게 된다.
     */
    fun preview(file: MultipartFile): ProblemImportPreview {
        val read = read(file)
        return ProblemImportPreview(
            source = when (read.source) {
                ProblemArchive.Source.ZIP -> ProblemImportPreview.BundleSource.ZIP
                ProblemArchive.Source.JSON -> ProblemImportPreview.BundleSource.JSON
            },
            slug = read.request.slug,
            title = read.request.title,
            category = read.request.category,
            problemKind = read.request.problemKind,
            difficulty = read.request.difficulty,
            timeLimitMs = read.request.timeLimitMs,
            memoryLimitMb = read.request.memoryLimitMb,
            testcaseCount = read.request.testcases.size,
            needsTestcases = read.request.problemKind.needsTestcases,
            testcaseSource = read.testcaseSource,
            templateCount = read.request.templates.size,
            publishedInBundle = read.publishedInBundle,
            // **던지지 않고 모아서 준다.** 첫 번째에서 멈추면 고치고 다시 올리기를 반복한다.
            violations = violationsOf(read.request),
        )
    }

    private data class Read(
        val request: ProblemUpsertRequest,
        val source: ProblemArchive.Source,
        val testcaseSource: ProblemImportPreview.TestcaseSource,
        val publishedInBundle: Boolean,
    )

    private fun read(file: MultipartFile): Read {
        if (file.isEmpty) throw ApiException(ErrorCode.VALIDATION_ERROR, "빈 파일입니다.")

        val content = file.inputStream.use(ProblemArchive::read)
        val meta = parse(resolveSchemaFile(content))
        val fromFiles = content.testcases.map { (seq, pair) ->
            TestcaseRequest(seq = seq, input = pair.first, expectedOutput = pair.second)
        }
        return Read(
            request = meta.copy(
                // 묶음의 테스트케이스가 본문의 것을 **이긴다.** 둘 다 있으면 큰 쪽이
                // 진짜다 — 파일로 뺀 이유가 그것이기 때문이다.
                testcases = fromFiles.ifEmpty { meta.testcases },
                // **언제나 초안이다.** 묶음이 무엇이라 적었든 덮는다.
                published = false,
            ),
            source = content.source,
            testcaseSource = when {
                fromFiles.isNotEmpty() -> ProblemImportPreview.TestcaseSource.FILES
                meta.testcases.isNotEmpty() -> ProblemImportPreview.TestcaseSource.INLINE
                else -> ProblemImportPreview.TestcaseSource.NONE
            },
            // 덮기 **전의** 값이다. 화면이 "적혀 있지만 초안으로 들어간다"를 말해야 한다.
            publishedInBundle = meta.published,
        )
    }

    /**
     * SQL 스키마를 묶음 안의 파일에서 끌어온다 (#561).
     *
     * ## 왜 파일인가
     *
     * `scripts/seed-problems` 의 SQL 문제 일곱 개가 이미 그렇게 하고 있다 —
     * 다섯 문제가 **같은 스키마를 공유**하고, 여러 줄 SQL 은 JSON 문자열 안에서 읽을 수
     * 없기 때문이다 (#313). 그것은 묶음이 테스트케이스를 파일로 뺀 이유와 똑같다.
     *
     * 그래서 형식을 새로 만들지 않고 **이미 있는 규칙을 한 자리 더 적용**한다.
     * `sqlSchemaFile` 은 시드가 쓰던 키 그대로다.
     *
     * ## 둘 다 적으면 거절한다
     *
     * 테스트케이스는 "파일이 이긴다" 지만 그쪽은 파일을 **넣기만** 하면 되는 것이고,
     * 여기는 `sqlSchemaFile` 을 **손으로 적어야** 한다. 손으로 적은 것 둘이 어긋나면
     * 어느 쪽이 뜻인지 우리가 정할 일이 아니다.
     */
    private fun resolveSchemaFile(content: ProblemArchive.Content): String {
        val root = objectMapper.readTree(content.meta) as? ObjectNode
            ?: throw ApiException(ErrorCode.VALIDATION_ERROR, "problem.json 이 객체가 아닙니다.")
        val schemaFile = root.remove(SCHEMA_FILE_KEY)?.takeIf { !it.isNull }?.asString()

        if (schemaFile != null) {
            val spec = root.get("sqlSpec") as? ObjectNode
                ?: throw ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "$SCHEMA_FILE_KEY 이 있는데 sqlSpec 이 없습니다.",
                )
            if (spec.hasNonNull("schemaSql")) {
                throw ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "$SCHEMA_FILE_KEY 과 sqlSpec.schemaSql 이 둘 다 있습니다. 하나만 적으십시오.",
                )
            }
            val schema = content.extras[schemaFile] ?: throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                if (content.source == ProblemArchive.Source.JSON) {
                    "$SCHEMA_FILE_KEY 은 묶음(zip) 안의 파일을 가리킵니다. 맨 JSON 으로는 읽을 수 없습니다: $schemaFile"
                } else {
                    "묶음에 $schemaFile 이 없습니다."
                },
            )
            spec.put("schemaSql", schema)
        }

        // **아무도 안 가리키는 파일은 거절한다.** 조용히 버리면 출제자는 자기가 넣은
        // 것이 들어갔다고 믿는다 — zip 의 "모르는 파일" 규칙 그대로다.
        val unused = content.extras.keys - setOfNotNull(schemaFile)
        if (unused.isNotEmpty()) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                buildString {
                    append("묶음에 모르는 파일이 있습니다: ${unused.sorted().joinToString()}")
                    // **무엇을 넣어야 하는지 함께 말한다** (#594). SQL 문제는 스키마
                    // 파일을 넣어야 하는데, 여러 개를 넣으면 안 쓰는 것이 걸린다 —
                    // 그때 "모르는 파일" 만 보이면 무엇이 잘못인지 알기 어렵다.
                    if (schemaFile != null) append(" (이 문제가 쓰는 파일은 $schemaFile 입니다)")
                },
            )
        }
        return root.toString()
    }

    private fun violationsOf(request: ProblemUpsertRequest): List<String> =
        validator.validate(request).map { "${it.propertyPath}: ${it.message}" }.sorted()

    /**
     * **모르는 키는 거절한다** (#537).
     *
     * zip 의 "모르는 파일이 있으면 거절한다" 와 같은 이유다 — 조용히 버리면 출제자는
     * 자기가 적은 것이 들어갔다고 믿는다.
     *
     * 실제로 그럴 자리가 있다. `scripts/seed-problems` 의 SQL 문제 일곱 개는
     * `sqlSchemaFile` 을 쓰는데, 그것은 **시드에서만 쓰는 키**이고
     * `seed-problems.sh` 가 보내기 전에 `sqlSpec.schemaSql` 로 조립해 없앤다 (#313).
     * 그 파일을 그대로 올리면 **스키마 없는 SQL 문제**가 될 뻔한 자리다.
     *
     * (그 일곱 개는 `schemaSql` 이 없다는 것이 먼저 걸려서 지금도 거절된다.
     * 이 설정은 **나머지 모르는 키**를 잡는다.)
     */
    private fun parse(meta: String): ProblemUpsertRequest = try {
        objectMapper.readerFor(ProblemUpsertRequest::class.java)
            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .readValue(meta)
    } catch (caught: Exception) {
        // 무엇이 잘못됐는지 그대로 전한다 — "잘못된 파일입니다" 로는 고칠 수 없다.
        throw ApiException(ErrorCode.VALIDATION_ERROR, "problem.json 을 읽지 못했습니다: ${caught.message}")
    }
}
