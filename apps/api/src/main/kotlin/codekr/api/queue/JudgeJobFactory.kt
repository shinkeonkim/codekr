package codekr.api.queue

import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemKind
import codekr.api.problem.repository.ProblemNoSqlSpecRepository
import codekr.api.problem.harness.ProblemHarnessRepository
import codekr.api.problem.repository.ProblemTestcaseGroupRepository
import codekr.api.problem.repository.ProblemSqlSpecRepository
import codekr.api.queue.message.JudgeJobMessage
import codekr.api.submission.entity.Submission
import org.springframework.stereotype.Component

/**
 * 채점 작업을 만든다 (#60).
 *
 * **유형별 자료를 싣는 곳을 한 군데로 모은다.** 제출·정답 검증·재채점 세 경로가
 * 각자 챙기게 두면, 새 유형이 생겼을 때 한 곳을 빠뜨려도 그 경로에서만 조용히
 * 잘못 채점된다.
 */
@Component
class JudgeJobFactory(
    private val groupRepository: ProblemTestcaseGroupRepository,
    private val harnessRepository: ProblemHarnessRepository,
    private val sqlSpecRepository: ProblemSqlSpecRepository,
    private val noSqlSpecRepository: ProblemNoSqlSpecRepository,
) {

    fun of(submission: Submission, problem: Problem): JudgeJobMessage = JudgeJobMessage.of(
        submission = submission,
        problem = problem,
        // 부분 점수 묶음 (#473). 없으면 빈 목록이고 채점은 지금까지와 같다.
        groups = groupRepository.findByProblemIdOrderByGroupNo(problem.id),
        // 제출한 언어의 하네스 (#421). 다른 언어의 것을 실으면 아예 돌지 않는다.
        harness = if (problem.problemKind == ProblemKind.JUDGE_FUNCTION) {
            harnessRepository.findByProblemIdAndRuntimeId(problem.id, submission.runtimeId)?.source
        } else {
            null
        },
        sqlSpec = when (problem.problemKind) {
            ProblemKind.JUDGE_SQL -> sqlSpecRepository.findById(problem.id).orElse(null)
            else -> null
        },
        noSqlSpec = when (problem.problemKind) {
            ProblemKind.JUDGE_NOSQL -> noSqlSpecRepository.findById(problem.id).orElse(null)
            else -> null
        },
    )
}
