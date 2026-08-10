package codekr.api.problem.repository

import codekr.api.problem.entity.QProblem
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.jpa.impl.JPAQueryFactory
import codekr.api.problem.entity.Problem
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

/** 문제 목록의 동적 검색. 조건 조합이 늘어나도 문자열 JPQL 을 붙이지 않도록 Querydsl 로 조립한다. */
@Repository
class ProblemSearchRepository(private val queryFactory: JPAQueryFactory) {

    fun search(condition: ProblemSearchCondition, pageable: Pageable): Page<Problem> {
        val problem = QProblem.problem
        val predicate = BooleanBuilder(problem.deletedAt.isNull).apply {
            condition.published?.let { and(problem.published.eq(it)) }
            condition.category?.let { and(problem.category.eq(it)) }
            // 티어는 연속된 레벨 구간이므로 범위 조건 하나로 걸린다.
            condition.tier?.let {
                and(problem.difficultyLevel.between(it.levelRange.first, it.levelRange.last))
            }
            condition.keyword?.takeIf { it.isNotBlank() }?.let {
                and(problem.title.containsIgnoreCase(it.trim()))
            }
        }

        val content = queryFactory
            .selectFrom(problem)
            .where(predicate)
            .orderBy(*orderBy(condition.sort))
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        val total = queryFactory
            .select(problem.count())
            .from(problem)
            .where(predicate)
            .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }

    private fun orderBy(sort: ProblemSort): Array<OrderSpecifier<*>> {
        val problem = QProblem.problem
        return when (sort) {
            ProblemSort.LATEST -> arrayOf(problem.createdAt.desc())
            ProblemSort.TITLE -> arrayOf(problem.title.asc())
            ProblemSort.DIFFICULTY -> arrayOf(problem.difficultyLevel.asc(), problem.title.asc())
        }
    }
}
