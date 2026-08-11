package codekr.api.contest.admin

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

/** 대회 등록/수정 (#61). 문제 배정은 항상 전체 치환된다 — 부분 수정은 순번이 꼬인다. */
data class ContestUpsertRequest(
    @field:Pattern(
        regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
        message = "slug 는 소문자, 숫자, 하이픈만 사용할 수 있습니다.",
    )
    @field:Size(min = 2, max = 120)
    val slug: String,

    @field:NotBlank @field:Size(max = 200)
    val title: String,

    val description: String = "",

    val startsAt: Instant,
    val endsAt: Instant,

    /** 0 이면 순위를 동결하지 않는다 (#86). */
    @field:Min(0)
    val freezeMinutes: Int = 30,

    val registrationOpenDuring: Boolean = true,

    @field:Valid
    val problems: List<ContestProblemRequest> = emptyList(),
)

data class ContestProblemRequest(
    val problemId: Long,
    @field:Min(1) val seq: Int,
    @field:Min(1) val score: Int,
)
