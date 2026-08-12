package codekr.api.tag.dto

import codekr.api.tag.entity.Tag

/**
 * 태그 하나 (#232).
 *
 * [problemCount] 를 함께 주는 이유: 없으면 고를 때 빈 결과를 계속 만난다. 태그 목록은
 * 고르는 화면이지 사전이 아니다.
 */
data class TagResponse(
    val id: Long,
    val slug: String,
    val name: String,
    val description: String?,
    val problemCount: Int,
) {
    companion object {
        fun of(tag: Tag, problemCount: Int) = TagResponse(
            id = tag.id,
            slug = tag.slug,
            name = tag.name,
            description = tag.description,
            problemCount = problemCount,
        )
    }
}

/** 문제에 붙은 태그. 문제 화면에서는 개수가 필요 없다. */
data class ProblemTagResponse(val id: Long, val slug: String, val name: String) {
    companion object {
        fun of(tag: Tag) = ProblemTagResponse(tag.id, tag.slug, tag.name)
    }
}
