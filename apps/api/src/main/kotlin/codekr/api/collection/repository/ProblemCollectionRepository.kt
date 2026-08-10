package codekr.api.collection.repository

import codekr.api.collection.entity.CollectionItemId
import codekr.api.collection.entity.ProblemCollection
import codekr.api.collection.entity.ProblemCollectionItem
import org.springframework.data.jpa.repository.JpaRepository

interface ProblemCollectionRepository : JpaRepository<ProblemCollection, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): ProblemCollection?

    fun findByShareTokenAndDeletedAtIsNull(shareToken: String): ProblemCollection?

    fun findByOwnerIdAndDeletedAtIsNullOrderByIdDesc(ownerId: Long): List<ProblemCollection>
}

interface ProblemCollectionItemRepository : JpaRepository<ProblemCollectionItem, CollectionItemId> {

    fun findByIdCollectionIdOrderBySeqAsc(collectionId: Long): List<ProblemCollectionItem>

    fun deleteByIdCollectionId(collectionId: Long)
}
