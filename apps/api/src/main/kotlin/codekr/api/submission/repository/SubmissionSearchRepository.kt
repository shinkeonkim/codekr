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

            condition.problemKey?.takeIf { it.isNotBlank() }?.let { key ->
                and(submission.problemId.`in`(problemIdsByKey(key)))
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

    /**
     * 번호와 slug 를 둘 다 푼다 — `ProblemService.findByKey` 와 같은 규칙이다 (#204, #600).
     *
     * **하나의 하위 질의로 둘 다 본다.** 번호로 먼저 찾아보고 없으면 slug 로 찾는 식으로
     * 나누면 질의가 두 번 나가고, 그 사이에 문제가 지워지면 결과가 달라진다. 숫자로만 된
     * slug 는 만들 수 없으므로 **둘이 동시에 맞는 일은 없다** — `or` 로 묶어도 안전하다.
     */
    private fun problemIdsByKey(key: String) =
        queryFactory.select(QProblem.problem.id).from(QProblem.problem)
            .where(
                key.toLongOrNull()
                    ?.let { QProblem.problem.id.eq(it).or(QProblem.problem.slug.eq(key)) }
                    ?: QProblem.problem.slug.eq(key),
            )

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
