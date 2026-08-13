package codekr.api.collection.repository

import codekr.api.collection.entity.CollectionItemId
import codekr.api.collection.entity.ProblemCollection
import codekr.api.collection.entity.ProblemCollectionItem
import org.springframework.data.jpa.repository.JpaRepository

interface ProblemCollectionRepository : JpaRepository<ProblemCollection, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): ProblemCollection?

    /**
     * 한 사람이 만든 공개 문제집 (#209).
     *
     * **공개된 것만이다.** 비공개가 남에게 새면 안 되고, 내 프로필에서도 같은 규칙을
     * 쓴다 — 내 것 전체는 `/collections` 가 보여 준다.
     */
    fun findByOwnerIdAndVisibilityAndDeletedAtIsNullOrderByIdDesc(
        ownerId: Long,
        visibility: codekr.api.collection.entity.CollectionVisibility,
    ): List<ProblemCollection>

    /**
     * 공개 목록 (#208).
     *
     * **최신순이다.** 인기순은 담은 사람 수나 조회수가 필요한데 둘 다 없다 — 대신
     * 어드민이 내리는 속도가 곧 목록의 품질이 된다는 것을 받아들인다.
     */
    fun findByVisibilityAndDeletedAtIsNull(
        visibility: codekr.api.collection.entity.CollectionVisibility,
        pageable: org.springframework.data.domain.Pageable,
    ): org.springframework.data.domain.Page<ProblemCollection>

    fun findByShareTokenAndDeletedAtIsNull(shareToken: String): ProblemCollection?

    fun findByOwnerIdAndDeletedAtIsNullOrderByIdDesc(ownerId: Long): List<ProblemCollection>
}

interface ProblemCollectionItemRepository : JpaRepository<ProblemCollectionItem, CollectionItemId> {

    fun findByIdCollectionIdOrderBySeqAsc(collectionId: Long): List<ProblemCollectionItem>

    fun deleteByIdCollectionId(collectionId: Long)

}
