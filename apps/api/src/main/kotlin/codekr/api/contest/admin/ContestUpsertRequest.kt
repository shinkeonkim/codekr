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

    /**
     * 같은 문제를 다시 낼 수 있기까지의 간격(초) (#189).
     *
     * **3초 아래로는 내릴 수 없다.** 제한이 없는 것과 같아지면 한 참가자가 채점 차선을
     * 혼자 채우고, 대회에서는 그것이 곧 다른 참가자의 불이익이다.
     */
    @field:Min(3, message = "제출 간격은 3초 이상이어야 합니다.")
    val submissionCooldownSeconds: Int = 20,

    val registrationOpenDuring: Boolean = true,

    @field:Valid
    val problems: List<ContestProblemRequest> = emptyList(),
)

data class ContestProblemRequest(
    val problemId: Long,
    @field:Min(1) val seq: Int,
    @field:Min(1) val score: Int,
)
