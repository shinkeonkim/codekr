package codekr.api.problem.repository

import codekr.api.problem.entity.QProblem
import codekr.api.tag.entity.QProblemTag
import codekr.api.tag.entity.QTag
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.jpa.JPAExpressions
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
                val keyword = it.trim()
                /*
                    번호로도 찾는다 (#204). 사람은 문제를 "1000번" 이라고 부르고,
                    그 숫자를 검색창에 넣는다.

                    제목 검색을 **대체하지 않고 더한다** — "3" 을 넣은 사람이 3번 문제를
                    찾는 것인지 제목에 3이 든 문제를 찾는 것인지 알 수 없다. 둘 다 보인다.
                */
                keyword.toLongOrNull()
                    ?.let { number -> and(problem.title.containsIgnoreCase(keyword).or(problem.id.eq(number))) }
                    ?: and(problem.title.containsIgnoreCase(keyword))
            }
            // 태그마다 "이 문제에 그 태그가 있는가" 를 하나씩 건다 (#232).
            //
            // 조인 한 번으로 IN 을 쓰면 **또는**이 된다. 조인을 태그 수만큼 늘리는 방법도
            // 있지만, 문제당 태그가 몇 개뿐이라 존재 검사가 읽기 쉽고 인덱스도 그대로 탄다.
            condition.tagSlugs.filter { it.isNotBlank() }.distinct().forEach { slug ->
                and(hasTag(problem, slug))
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

    private fun hasTag(problem: QProblem, slug: String) = JPAExpressions
        .selectOne()
        .from(QProblemTag.problemTag)
        .join(QTag.tag).on(QTag.tag.id.eq(QProblemTag.problemTag.id.tagId))
        .where(QProblemTag.problemTag.id.problemId.eq(problem.id), QTag.tag.slug.eq(slug))
        .exists()

    private fun orderBy(sort: ProblemSort): Array<OrderSpecifier<*>> {
        val problem = QProblem.problem
        return when (sort) {
            ProblemSort.LATEST -> arrayOf(problem.createdAt.desc())
            ProblemSort.TITLE -> arrayOf(problem.title.asc())
            ProblemSort.DIFFICULTY -> arrayOf(problem.difficultyLevel.asc(), problem.title.asc())
        }
    }
}
