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

    /**
     * 내 문제집 목록 (#601).
     *
     * **페이지가 없으면 통째로 실어 보낸다.** 공개 목록은 처음부터 `size` 를 받는데
     * 내 목록만 `List` 였다 — 지금은 몇 개 안 만들어 티가 안 나지만, 늘어나면 한 번에
     * 다 나간다.
     */
    fun findByOwnerIdAndDeletedAtIsNullOrderByIdDesc(
        ownerId: Long,
        pageable: org.springframework.data.domain.Pageable,
    ): org.springframework.data.domain.Page<ProblemCollection>
}

interface ProblemCollectionItemRepository : JpaRepository<ProblemCollectionItem, CollectionItemId> {

    fun findByIdCollectionIdOrderBySeqAsc(collectionId: Long): List<ProblemCollectionItem>

    fun deleteByIdCollectionId(collectionId: Long)

}
