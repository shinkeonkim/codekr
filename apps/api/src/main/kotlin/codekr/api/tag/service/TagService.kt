package codekr.api.tag.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.repository.ProblemRepository
import codekr.api.tag.dto.ProblemTagResponse
import codekr.api.tag.dto.TagResponse
import codekr.api.tag.entity.ProblemTag
import codekr.api.tag.entity.ProblemTagId
import codekr.api.tag.entity.Tag
import codekr.api.tag.repository.ProblemTagRepository
import codekr.api.tag.repository.TagRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 태그와 문제-태그 관계 (#232).
 *
 * 태그를 다는 것은 **어드민만** 한다. "푼 사람이 제안하고 어드민이 확정" 은 규모가 커지면
 * 유일한 방법이지만, 제안·검토 흐름을 통째로 들여온다 — 지금 필요한 것은 분류지 흐름이 아니다.
 */
@Service
@Transactional(readOnly = true)
class TagService(
    private val tagRepository: TagRepository,
    private val problemTagRepository: ProblemTagRepository,
    private val problemRepository: ProblemRepository,
) {

    /** 태그 목록. 각 태그에 걸린 **공개 문제 수**를 함께 준다. */
    fun list(): List<TagResponse> {
        val counts = problemTagRepository.countPublishedByTag()
            .associate { (tagId, count) -> tagId as Long to (count as Long).toInt() }
        return tagRepository.findAllByOrderByNameAsc().map { TagResponse.of(it, counts[it.id] ?: 0) }
    }

    fun tagsOf(problemId: Long): List<ProblemTagResponse> {
        val tagIds = problemTagRepository.findByIdProblemId(problemId).map { it.tagId }
        if (tagIds.isEmpty()) return emptyList()
        // 이름순으로 고정한다 — 새로 고칠 때마다 순서가 바뀌면 읽는 사람이 다른 것으로 착각한다.
        return tagRepository.findAllById(tagIds).sortedBy { it.name }.map(ProblemTagResponse::of)
    }

    @Transactional
    fun create(slug: String, name: String, description: String?): TagResponse {
        if (tagRepository.existsBySlug(slug)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이미 있는 태그 주소입니다: $slug")
        }
        if (tagRepository.existsByName(name)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이미 있는 태그 이름입니다: $name")
        }
        return TagResponse.of(tagRepository.save(Tag(slug, name, description)), 0)
    }

    @Transactional
    fun update(id: Long, name: String, description: String?): TagResponse {
        val tag = tagRepository.findById(id).orElseThrow { ApiException(ErrorCode.TAG_NOT_FOUND) }
        // 주소(slug)는 바꾸지 않는다 — 링크와 필터 파라미터가 그것을 가리키고 있다.
        if (tag.name != name && tagRepository.existsByName(name)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이미 있는 태그 이름입니다: $name")
        }
        tag.name = name
        tag.description = description
        return TagResponse.of(tag, problemCountOf(tag.id))
    }

    /**
     * 문제의 태그를 **통째로 바꾼다.**
     *
     * 하나씩 더하고 빼는 API 로 두지 않는 이유: 어드민 화면이 여러 개를 골라 저장하는
     * 형태라, 부분 갱신으로 두면 화면과 서버가 서로 다른 상태를 갖는 구간이 생긴다.
     */
    @Transactional
    fun replaceTagsOf(problemId: Long, tagIds: List<Long>): List<ProblemTagResponse> {
        if (!problemRepository.existsByIdAndDeletedAtIsNull(problemId)) {
            throw ApiException(ErrorCode.PROBLEM_NOT_FOUND)
        }
        val distinct = tagIds.distinct()
        val tags = tagRepository.findAllById(distinct)
        if (tags.size != distinct.size) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "없는 태그가 포함되어 있습니다.")
        }

        problemTagRepository.deleteByIdProblemId(problemId)
        // 지운 뒤 바로 같은 키를 넣으므로, 같은 트랜잭션 안에서 순서를 강제한다.
        problemTagRepository.flush()
        problemTagRepository.saveAll(distinct.map { ProblemTag(ProblemTagId(problemId, it)) })

        return tags.sortedBy { it.name }.map(ProblemTagResponse::of)
    }

    private fun problemCountOf(tagId: Long): Int =
        problemTagRepository.countPublishedByTag()
            .firstOrNull { (id, _) -> id == tagId }
            ?.let { (_, count) -> (count as Long).toInt() } ?: 0
}
