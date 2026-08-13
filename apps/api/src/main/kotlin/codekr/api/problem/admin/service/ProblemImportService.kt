package codekr.api.problem.admin.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.admin.dto.ProblemCreatedResponse
import codekr.api.problem.admin.dto.ProblemUpsertRequest
import codekr.api.problem.admin.dto.TestcaseRequest
import jakarta.validation.Validator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import tools.jackson.databind.ObjectMapper

/**
 * 묶음 파일로 문제를 만든다 (#479).
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

    @Transactional
    fun import(file: MultipartFile, createdBy: Long): ProblemCreatedResponse {
        if (file.isEmpty) throw ApiException(ErrorCode.VALIDATION_ERROR, "빈 파일입니다.")

        val content = file.inputStream.use(ProblemArchive::read)
        val request = parse(content.meta).let { meta ->
            meta.copy(
                // 묶음의 테스트케이스가 본문의 것을 **이긴다.** 둘 다 있으면 큰 쪽이
                // 진짜다 — 파일로 뺀 이유가 그것이기 때문이다.
                testcases = content.testcases.map { (seq, pair) ->
                    TestcaseRequest(seq = seq, input = pair.first, expectedOutput = pair.second)
                }.ifEmpty { meta.testcases },
                // **언제나 초안이다.** 묶음이 무엇이라 적었든 덮는다.
                published = false,
            )
        }

        // 폼으로 만들 때와 **같은 규칙**을 지난다 (#59, #60, #454, #455).
        // 여기서만 통과하는 길을 두면 그 길로 들어온 문제가 화면에서 고쳐지지 않는다.
        val violations = validator.validate(request)
        if (violations.isNotEmpty()) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                violations.joinToString { "${it.propertyPath}: ${it.message}" },
            )
        }
        return adminProblemService.create(request, createdBy)
    }

    private fun parse(meta: String): ProblemUpsertRequest = try {
        objectMapper.readValue(meta, ProblemUpsertRequest::class.java)
    } catch (caught: Exception) {
        // 무엇이 잘못됐는지 그대로 전한다 — "잘못된 파일입니다" 로는 고칠 수 없다.
        throw ApiException(ErrorCode.VALIDATION_ERROR, "problem.json 을 읽지 못했습니다: ${caught.message}")
    }
}
