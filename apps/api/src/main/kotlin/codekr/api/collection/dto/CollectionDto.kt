package codekr.api.collection.dto

import codekr.api.collection.entity.CollectionVisibility
import codekr.api.collection.entity.ProblemCollection
import codekr.api.problem.entity.Difficulty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

/** 문제집 등록·수정 (#87). 담긴 문제는 항상 전체 치환된다 — 부분 수정은 순번이 꼬인다. */
data class CollectionUpsertRequest(
    @field:NotBlank(message = "문제집 이름이 필요합니다.")
    @field:Size(max = 120)
    val name: String,

    @field:Size(max = 2000)
    val description: String = "",

    val visibility: CollectionVisibility = CollectionVisibility.PRIVATE,

    /**
     * 담을 문제의 id. **순서가 곧 문제집의 순서다.**
     *
     * 만드는 중에는 비어 있을 수 있다 — 최소 개수는 공유할 때 본다.
     */
    val problemIds: List<Long> = emptyList(),
)

data class CollectionSummaryResponse(
    val id: Long,
    val name: String,
    val description: String,
    val visibility: CollectionVisibility,
    val visibilityLabel: String,
    /** 링크 공유 주소에 쓰는 값. 소유자에게만 내린다. */
    val shareToken: String?,
    val problemCount: Int,
    /** 그 사람이 이 문제집에서 몇 개를 풀었는지. 비로그인이면 0. */
    val solvedCount: Int,
    val ownerNickname: String,
    val createdAt: Instant,
) {
    companion object {
        fun of(
            collection: ProblemCollection,
            ownerNickname: String,
            problemCount: Int,
            solvedCount: Int,
            forOwner: Boolean,
        ) = CollectionSummaryResponse(
            id = collection.id,
            name = collection.name,
            description = collection.description,
            visibility = collection.visibility,
            visibilityLabel = collection.visibility.label,
            // 링크를 아는 사람만 볼 수 있다는 말이 성립하려면 남에게 토큰을 주면 안 된다.
            shareToken = collection.shareToken.takeIf { forOwner },
            problemCount = problemCount,
            solvedCount = solvedCount,
            ownerNickname = ownerNickname,
            createdAt = collection.createdAt,
        )
    }
}

data class CollectionDetailResponse(
    val summary: CollectionSummaryResponse,
    val editable: Boolean,
    val problems: List<CollectionProblemResponse>,
)

data class CollectionProblemResponse(
    /** 문제 번호 (#204). 주소가 번호로 간다. */
    val id: Long,
    val slug: String,
    val title: String,
    /** 화면이 난이도 뱃지를 그대로 쓰도록 티어가 아니라 난이도를 준다. */
    /** 미평가·평가안함이면 `null` 이다 (#195). */
    val difficulty: Difficulty?,
    val difficultyLabel: String,
    /** 그 사람이 이미 푼 문제인가. 진행률을 줄로 세어 보여주기 위함이다. */
    val solved: Boolean,
)
