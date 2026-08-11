package codekr.api.collection.service

import codekr.api.collection.dto.CollectionDetailResponse
import codekr.api.collection.dto.CollectionProblemResponse
import codekr.api.collection.dto.CollectionSummaryResponse
import codekr.api.collection.dto.CollectionUpsertRequest
import codekr.api.collection.entity.CollectionItemId
import codekr.api.collection.entity.CollectionVisibility
import codekr.api.collection.entity.ProblemCollection
import codekr.api.collection.entity.ProblemCollectionItem
import codekr.api.collection.repository.ProblemCollectionItemRepository
import codekr.api.collection.repository.ProblemCollectionRepository
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.entity.Problem
import codekr.api.problem.repository.ProblemRepository
import codekr.api.user.entity.WithdrawnUser
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 문제집 (#87).
 *
 * **최소 2개 제약은 공유할 때 본다.** 만드는 중에는 0개인 순간이 반드시 있고,
 * 저장 시점에 막으면 이름만 정해 두고 나중에 채우는 흔한 흐름이 불가능해진다.
 */
@Service
@Transactional(readOnly = true)
class ProblemCollectionService(
    private val collectionRepository: ProblemCollectionRepository,
    private val itemRepository: ProblemCollectionItemRepository,
    private val problemRepository: ProblemRepository,
    private val userRepository: UserRepository,
    private val progressRepository: CollectionProgressRepository,
) {

    fun findMine(userId: Long): List<CollectionSummaryResponse> {
        val nickname = nicknameOf(userId)
        return collectionRepository.findByOwnerIdAndDeletedAtIsNullOrderByIdDesc(userId)
            .map { summaryOf(it, nickname, userId, forOwner = true) }
    }

    /** 남의 문제집은 링크(공유 토큰)로만 연다. 번호로 훑을 수 없다. */
    fun findByToken(shareToken: String, viewerId: Long?): CollectionDetailResponse {
        val collection = collectionRepository.findByShareTokenAndDeletedAtIsNull(shareToken)
            ?: throw ApiException(ErrorCode.COLLECTION_NOT_FOUND)
        return detailOf(collection, viewerId)
    }

    fun findOne(id: Long, viewerId: Long?): CollectionDetailResponse {
        val collection = collectionRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw ApiException(ErrorCode.COLLECTION_NOT_FOUND)
        // 비공개 문제집은 없는 것과 같다 — 존재 여부도 알리지 않는다.
        if (!collection.isVisibleTo(viewerId)) throw ApiException(ErrorCode.COLLECTION_NOT_FOUND)
        return detailOf(collection, viewerId)
    }

    @Transactional
    fun create(userId: Long, request: CollectionUpsertRequest): CollectionDetailResponse {
        validate(request)
        val collection = collectionRepository.save(
            ProblemCollection(userId, request.name, request.description, request.visibility),
        )
        replaceItems(collection.id, request.problemIds)
        return detailOf(collection, userId)
    }

    @Transactional
    fun update(id: Long, userId: Long, request: CollectionUpsertRequest): CollectionDetailResponse {
        val collection = requireOwned(id, userId)
        validate(request)

        collection.name = request.name
        collection.description = request.description
        collection.visibility = request.visibility
        replaceItems(collection.id, request.problemIds)
        return detailOf(collection, userId)
    }

    @Transactional
    fun delete(id: Long, userId: Long) = requireOwned(id, userId).delete()

    /**
     * 공유 가능한 상태인지 확인한다.
     *
     * **문제가 2개 미만이면 공유할 수 없다.** 1개짜리 묶음은 묶음이 아니다 —
     * 문제 하나를 보내려면 문제 링크를 보내면 된다.
     */
    private fun validate(request: CollectionUpsertRequest) {
        if (request.problemIds.toSet().size != request.problemIds.size) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "같은 문제를 두 번 담을 수 없습니다.")
        }
        if (request.visibility != CollectionVisibility.PRIVATE && request.problemIds.size < MIN_SHARED_PROBLEMS) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "공유하려면 문제가 ${MIN_SHARED_PROBLEMS}개 이상이어야 합니다.",
            )
        }
        request.problemIds.forEach { problemId ->
            problemRepository.findByIdAndDeletedAtIsNull(problemId)
                ?: throw ApiException(ErrorCode.PROBLEM_NOT_FOUND, "없는 문제를 담았습니다: $problemId")
        }
    }

    private fun replaceItems(collectionId: Long, problemIds: List<Long>) {
        itemRepository.deleteByIdCollectionId(collectionId)
        itemRepository.flush()
        itemRepository.saveAll(
            problemIds.mapIndexed { index, problemId ->
                ProblemCollectionItem(CollectionItemId(collectionId, problemId), index + 1)
            },
        )
    }

    private fun detailOf(collection: ProblemCollection, viewerId: Long?): CollectionDetailResponse {
        val problems = livingProblems(collection.id)
        val solved = viewerId?.let { progressRepository.solvedProblemIds(it, problems.map(Problem::id)) }
            ?: emptySet()

        return CollectionDetailResponse(
            summary = CollectionSummaryResponse.of(
                collection,
                nicknameOf(collection.ownerId),
                problems.size,
                solved.size,
                forOwner = collection.ownerId == viewerId,
            ),
            editable = collection.ownerId == viewerId,
            problems = problems.map {
                CollectionProblemResponse(
                    slug = it.slug,
                    title = it.title,
                    difficulty = it.difficulty,
                    difficultyLabel = it.difficulty.label,
                    solved = it.id in solved,
                )
            },
        )
    }

    /**
     * 삭제된 문제는 목록에서 뺀다. **행은 지우지 않는다** (ADR-0007).
     *
     * 복구되면 다시 나타나야 한다. 여기서 지우면 순서 정보까지 사라진다.
     */
    private fun livingProblems(collectionId: Long): List<Problem> {
        val items = itemRepository.findByIdCollectionIdOrderBySeqAsc(collectionId)
        val byId = problemRepository.findAllById(items.map { it.problemId })
            .filter { it.deletedAt == null }
            .associateBy { it.id }
        return items.mapNotNull { byId[it.problemId] }
    }

    private fun summaryOf(
        collection: ProblemCollection,
        ownerNickname: String,
        viewerId: Long?,
        forOwner: Boolean,
    ): CollectionSummaryResponse {
        val problems = livingProblems(collection.id)
        val solved = viewerId?.let { progressRepository.solvedProblemIds(it, problems.map(Problem::id)) }
            ?: emptySet()
        return CollectionSummaryResponse.of(collection, ownerNickname, problems.size, solved.size, forOwner)
    }

    private fun requireOwned(id: Long, userId: Long): ProblemCollection {
        val collection = collectionRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw ApiException(ErrorCode.COLLECTION_NOT_FOUND)
        // 남의 문제집을 고칠 수 없다. 있는지조차 알리지 않는다.
        if (collection.ownerId != userId) throw ApiException(ErrorCode.COLLECTION_NOT_FOUND)
        return collection
    }

    private fun nicknameOf(userId: Long): String =
        WithdrawnUser.nicknameOf(userRepository.findById(userId).orElse(null))

    private companion object {
        /** 1개짜리 묶음은 묶음이 아니다 — 문제 하나를 보내려면 문제 링크를 보내면 된다. */
        const val MIN_SHARED_PROBLEMS = 2
    }
}
