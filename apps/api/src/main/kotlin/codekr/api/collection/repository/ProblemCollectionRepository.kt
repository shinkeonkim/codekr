package codekr.api.collection.repository

import codekr.api.collection.entity.CollectionItemId
import codekr.api.collection.entity.ProblemCollection
import codekr.api.collection.entity.ProblemCollectionItem
import org.springframework.data.jpa.repository.JpaRepository

interface ProblemCollectionRepository : JpaRepository<ProblemCollection, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): ProblemCollection?

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
