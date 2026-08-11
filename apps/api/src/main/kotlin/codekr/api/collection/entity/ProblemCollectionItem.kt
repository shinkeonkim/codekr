package codekr.api.collection.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable

/** 문제집에 담긴 문제와 순서 (#87). */
@Entity
@Table(name = "problem_collection_items")
class ProblemCollectionItem(
    @EmbeddedId
    val id: CollectionItemId,

    @Column(nullable = false)
    var seq: Int,
) {
    val problemId: Long get() = id.problemId
}

@Embeddable
data class CollectionItemId(
    @Column(name = "collection_id") val collectionId: Long = 0,
    @Column(name = "problem_id") val problemId: Long = 0,
) : Serializable
