package codekr.api.submission.repository

import codekr.api.problem.entity.QProblem
import codekr.api.submission.entity.QSubmission
import codekr.api.submission.entity.Submission
import codekr.api.submission.entity.SubmissionKind
import codekr.api.user.entity.QUser
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

/** 전체 제출 목록의 동적 검색. 조건이 늘어나도 문자열 JPQL 을 붙이지 않는다. */
@Repository
class SubmissionSearchRepository(private val queryFactory: JPAQueryFactory) {

    fun search(condition: SubmissionSearchCondition, pageable: Pageable): Page<Submission> {
        val submission = QSubmission.submission
        val predicate = buildPredicate(condition)

        val content = queryFactory
            .selectFrom(submission)
            .where(predicate)
            .orderBy(*orderBy(condition.sort))
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        val total = queryFactory
            .select(submission.count())
            .from(submission)
            .where(predicate)
            .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }

    private fun buildPredicate(condition: SubmissionSearchCondition): BooleanBuilder {
        val submission = QSubmission.submission
        return BooleanBuilder(submission.deletedAt.isNull).apply {
            // 정답 코드 검증 제출은 어떤 사용자 목록에도 나타나지 않는다 (#39).
            and(submission.kind.eq(SubmissionKind.USER))

            condition.problemSlug?.takeIf { it.isNotBlank() }?.let { slug ->
                and(submission.problemId.`in`(problemIdsBySlug(slug)))
            }
            condition.nickname?.takeIf { it.isNotBlank() }?.let { nickname ->
                and(submission.userId.`in`(userIdsByNickname(nickname)))
            }
            condition.runtimeId?.takeIf { it.isNotBlank() }?.let { and(submission.runtimeId.eq(it)) }
            condition.verdict?.let { and(submission.verdict.eq(it)) }
            condition.submittedFrom?.let { and(submission.createdAt.goe(it)) }
            condition.submittedTo?.let { and(submission.createdAt.lt(it)) }
        }
    }

    private fun problemIdsBySlug(slug: String) =
        queryFactory.select(QProblem.problem.id).from(QProblem.problem)
            .where(QProblem.problem.slug.eq(slug))

    private fun userIdsByNickname(nickname: String) =
        queryFactory.select(QUser.user.id).from(QUser.user)
            .where(QUser.user.nickname.containsIgnoreCase(nickname.trim()))

    /**
     * 정렬은 항상 id 를 마지막 키로 둔다. 같은 값이 여러 개일 때 페이지 사이에서
     * 순서가 흔들리면 중복·누락이 생긴다.
     */
    private fun orderBy(sort: SubmissionSort): Array<OrderSpecifier<*>> {
        val submission = QSubmission.submission
        return when (sort) {
            SubmissionSort.LATEST -> arrayOf(submission.createdAt.desc(), submission.id.desc())
            SubmissionSort.OLDEST -> arrayOf(submission.createdAt.asc(), submission.id.asc())
            SubmissionSort.RUNTIME -> arrayOf(submission.maxRuntimeMs.asc(), submission.id.desc())
            SubmissionSort.MEMORY -> arrayOf(submission.maxMemoryKb.asc(), submission.id.desc())
        }
    }
}
