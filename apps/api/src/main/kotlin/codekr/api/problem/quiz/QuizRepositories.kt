package codekr.api.problem.quiz

import codekr.api.problem.entity.ProblemQuizAnswer
import codekr.api.problem.entity.ProblemQuizChoice
import codekr.api.problem.entity.ProblemQuizSpec
import org.springframework.data.jpa.repository.JpaRepository

/** 퀴즈 설정 (#650). 문제 하나에 하나다. */
interface ProblemQuizSpecRepository : JpaRepository<ProblemQuizSpec, Long>

/**
 * 보기 (#650).
 *
 * **통째로 갈아 끼운다** — 번호가 바뀌면 그것은 다른 보기이고, 무엇이 무엇의 개정인지
 * 서버가 짐작하면 정답 표시가 엉뚱한 줄에 남는다. 테스트케이스 묶음(#473)·파일 목록
 * (#457)이 같은 판단을 했다.
 */
interface ProblemQuizChoiceRepository : JpaRepository<ProblemQuizChoice, Long> {
    fun findByProblemIdOrderBySeqAsc(problemId: Long): List<ProblemQuizChoice>
    fun deleteByProblemId(problemId: Long)
}

/** 단답으로 받아 줄 답 (#650). 보기와 같은 이유로 통째로 갈아 끼운다. */
interface ProblemQuizAnswerRepository : JpaRepository<ProblemQuizAnswer, Long> {
    fun findByProblemIdOrderBySeqAsc(problemId: Long): List<ProblemQuizAnswer>
    fun deleteByProblemId(problemId: Long)
}
