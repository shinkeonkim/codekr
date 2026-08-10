package codekr.api.common.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import java.time.Instant

/**
 * 삭제 시각만 남기고 행은 보존하는 엔티티의 상위 타입.
 *
 * 물리 삭제 대신 소프트 삭제를 쓰는 이유는, 문제가 지워져도 그 문제로 만든 제출 이력이
 * 사용자에게 의미 있는 기록으로 남아야 하기 때문이다.
 *
 * 조회 필터는 Hibernate 의 전역 제약(@SQLRestriction)이 아니라 리포지토리에서 명시적으로 건다.
 * 제출 이력 화면처럼 "삭제된 문제까지 함께 봐야 하는" 경로가 존재하기 때문이다.
 */
@MappedSuperclass
abstract class SoftDeletableEntity : BaseTimeEntity() {

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
        protected set

    val isDeleted: Boolean get() = deletedAt != null

    /** 이미 삭제된 대상을 다시 삭제해도 최초 삭제 시각을 유지한다. */
    fun softDelete(now: Instant = Instant.now()) {
        if (deletedAt == null) deletedAt = now
    }

    fun restore() {
        deletedAt = null
    }
}
