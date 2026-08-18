package codekr.api.problem.repository

import codekr.api.problem.entity.QProblem
import codekr.api.problem.entity.QProblemAllowedRuntime
import codekr.api.ranking.entity.QUserProblemScoreRef
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.Expressions
import java.math.BigDecimal
import java.math.RoundingMode
import codekr.api.problem.entity.QProblemStat
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
private val HUNDRED = BigDecimal(100)

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
            /*
                난이도 상태로도 고른다 (#195).

                티어 필터의 `BETWEEN` 은 **레벨이 없는 문제를 어느 구간에도 넣지 않는다** —
                고를 방법이 따로 있어야 미평가 문제가 목록에서 사라지지 않는다.
            */
            condition.difficultyState?.let { and(problem.difficultyState.eq(it)) }
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
            condition.problemKind?.let { and(problem.problemKind.eq(it)) }

            /*
                언어·런타임 (#618).

                **"제한 없음" 을 함께 걸지 않으면 거의 아무것도 안 나온다.** 허용 목록에
                행이 있는 문제는 소수이고(#419 가 기존 문제를 그대로 돌리려고 그렇게 정했다),
                나머지는 유형이 허용을 정한다.
            */
            condition.runtimeFilter?.let { filter ->
                and(
                    when {
                        // 모르는 언어·런타임을 골랐다. 빈 목록이 맞다 — 조건을 지우면
                        // **거른 적 없는 것처럼** 전부 나온다.
                        filter.isEmpty -> Expressions.FALSE.isTrue
                        filter.kinds.isEmpty() -> allows(problem, filter.runtimeIds)
                        else -> allows(problem, filter.runtimeIds)
                            .or(unrestricted(problem).and(problem.problemKind.`in`(filter.kinds)))
                    },
                )
            }

            /*
                정답률·푼 사람 수 범위 (#239).

                **제출자가 없는 문제는 어느 범위에도 들지 않는다.** 정답률이 `NULL` 이라
                비교가 참이 되지 않는다 — `0/0` 은 0% 가 아니다 (#205).
            */
            val stat = QProblemStat.problemStat
            condition.acceptanceFrom?.let { and(statExists(stat.acceptance.goe(ratio(it)))) }
            condition.acceptanceTo?.let { and(statExists(stat.acceptance.loe(ratio(it)))) }
            condition.solversFrom?.let { and(statExists(stat.solvers.goe(it))) }
            condition.solversTo?.let { and(statExists(stat.solvers.loe(it))) }

            /*
                내가 푼 것 / 안 푼 것 (#239).

                **목록을 여는 가장 흔한 이유인데 방법이 없었다.** 해결 기록은 랭킹 점수
                표에 있다 (#57) — 거기 행이 있으면 푼 것이다.

                비로그인이면 `viewerId` 가 없고, 그때 이 조건 자체가 걸리지 않는다.
            */
            val viewerId = condition.viewerId
            if (viewerId != null && condition.solved != null) {
                val solvedProblem = solvedBy(problem, viewerId)
                if (condition.solved) and(solvedProblem) else and(solvedProblem.not())
            }
        }

        val stat = QProblemStat.problemStat
        val query = queryFactory.selectFrom(problem)
        // 통계로 정렬할 때만 조인한다. 늘 붙이면 다른 정렬까지 조인 비용을 낸다.
        if (needsStats(condition.sort)) query.leftJoin(stat).on(stat.problemId.eq(problem.id))

        val content = query
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

    /** 백분율을 비율로. 저장된 정답률은 0~1 이다. */
    private fun ratio(percent: Int): BigDecimal =
        BigDecimal(percent).divide(HUNDRED, 6, RoundingMode.HALF_UP)

    /**
     * 통계 조건을 **존재 검사로** 건다.
     *
     * 조인으로 걸면 정렬용 조인과 겹쳐 같은 문제가 두 번 나올 수 있다. 존재 검사는
     * 조인 상태와 무관하게 같은 뜻이다.
     */
    private fun statExists(condition: BooleanExpression): Predicate {
        val stat = QProblemStat.problemStat
        val problem = QProblem.problem
        return JPAExpressions.selectOne()
            .from(stat)
            .where(stat.problemId.eq(problem.id), condition)
            .exists()
    }

    /** 그 사람이 푼 문제인가. 점수 표에 행이 있으면 푼 것이다 (#57). */
    private fun solvedBy(problem: QProblem, userId: Long) = JPAExpressions
        .selectOne()
        .from(QUserProblemScoreRef.userProblemScoreRef)
        .where(
            QUserProblemScoreRef.userProblemScoreRef.problemId.eq(problem.id),
            QUserProblemScoreRef.userProblemScoreRef.userId.eq(userId),
        )
        .exists()

    /** 그 런타임들 중 하나라도 **명시적으로 허용**했는가 (#419). */
    private fun allows(problem: QProblem, runtimeIds: List<String>) = JPAExpressions
        .selectOne()
        .from(QProblemAllowedRuntime.problemAllowedRuntime)
        .where(
            QProblemAllowedRuntime.problemAllowedRuntime.problem.id.eq(problem.id),
            QProblemAllowedRuntime.problemAllowedRuntime.runtimeId.`in`(runtimeIds),
        )
        .exists()

    /**
     * 허용 목록이 **비어 있는가.** 비어 있으면 그 유형을 푸는 런타임 전부가 허용이다.
     *
     * 이 조건이 이 필터의 핵심이다 — 빼면 언어를 지정한 소수만 걸린다.
     */
    private fun unrestricted(problem: QProblem) = JPAExpressions
        .selectOne()
        .from(QProblemAllowedRuntime.problemAllowedRuntime)
        .where(QProblemAllowedRuntime.problemAllowedRuntime.problem.id.eq(problem.id))
        .notExists()

    private fun hasTag(problem: QProblem, slug: String) = JPAExpressions
        .selectOne()
        .from(QProblemTag.problemTag)
        .join(QTag.tag).on(QTag.tag.id.eq(QProblemTag.problemTag.id.tagId))
        .where(QProblemTag.problemTag.id.problemId.eq(problem.id), QTag.tag.slug.eq(slug))
        .exists()

    /**
     * 정렬 기준 (#132, #205).
     *
     * **두 번째 기준을 늘 둔다.** 정답률이 같은 문제가 여럿일 때 차례가 매번 달라지면
     * 쪽을 넘길 때 같은 문제가 두 번 나오거나 아예 빠진다.
     *
     * 통계가 없는 문제(제출자 0)는 `nullsLast` 로 **뒤로 보낸다** — 정답률이 없는 것을
     * 0% 로 보면 목록 맨 앞이 새 문제로만 찬다.
     */
    private fun orderBy(sort: ProblemSort): Array<OrderSpecifier<*>> {
        val problem = QProblem.problem
        val stat = QProblemStat.problemStat
        return when (sort) {
            ProblemSort.LATEST -> arrayOf(problem.createdAt.desc())
            ProblemSort.OLDEST -> arrayOf(problem.createdAt.asc())
            ProblemSort.TITLE -> arrayOf(problem.title.asc())
            /*
                난이도가 없는 문제는 **양쪽 모두 뒤로 간다** (#195).

                `nullsLast` 하나로 끝나지 않는다 — 내림차순에서 SQL 의 기본은
                `NULLS FIRST` 라, 그냥 두면 "어려운순" 맨 앞이 미평가 문제로 찬다.
                순서 없는 값을 정수 정렬에 우연히 섞이게 두지 않는다.
            */
            ProblemSort.DIFFICULTY ->
                arrayOf(problem.difficultyLevel.asc().nullsLast(), problem.title.asc())
            ProblemSort.DIFFICULTY_DESC ->
                arrayOf(problem.difficultyLevel.desc().nullsLast(), problem.title.asc())
            ProblemSort.SOLVERS_DESC -> arrayOf(stat.solvers.desc().nullsLast(), problem.id.asc())
            ProblemSort.SOLVERS_ASC -> arrayOf(stat.solvers.asc().nullsLast(), problem.id.asc())
            ProblemSort.ACCEPTANCE_DESC -> arrayOf(stat.acceptance.desc().nullsLast(), problem.id.asc())
            ProblemSort.ACCEPTANCE_ASC -> arrayOf(stat.acceptance.asc().nullsLast(), problem.id.asc())
        }
    }

    /** 통계를 봐야 하는 정렬인가. 아니면 조인을 붙이지 않는다. */
    private fun needsStats(sort: ProblemSort) = sort in setOf(
        ProblemSort.SOLVERS_DESC,
        ProblemSort.SOLVERS_ASC,
        ProblemSort.ACCEPTANCE_DESC,
        ProblemSort.ACCEPTANCE_ASC,
    )
}
