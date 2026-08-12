package codekr.api.tag.repository

import codekr.api.tag.entity.Tag
import org.springframework.data.jpa.repository.JpaRepository

interface TagRepository : JpaRepository<Tag, Long> {

    fun findBySlug(slug: String): Tag?

    fun existsBySlug(slug: String): Boolean

    fun existsByName(name: String): Boolean

    fun findAllByOrderByNameAsc(): List<Tag>
}
