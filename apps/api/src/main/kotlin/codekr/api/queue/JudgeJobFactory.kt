package codekr.api.queue

import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemKind
import codekr.api.problem.repository.ProblemMongoSpecRepository
import codekr.api.problem.repository.ProblemRedisSpecRepository
import codekr.api.problem.repository.ProblemGitSpecRepository
import codekr.api.problem.repository.ProblemMutantRepository
import codekr.api.problem.repository.ProblemMutationSpecRepository
import codekr.api.queue.message.JudgeMutationSpecMessage
import codekr.api.problem.repository.ProblemRegexSpecRepository
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
    private val sqlSpecRepository: ProblemSqlSpecRepository,
    private val redisSpecRepository: ProblemRedisSpecRepository,
    private val mongoSpecRepository: ProblemMongoSpecRepository,
    private val regexSpecRepository: ProblemRegexSpecRepository,
    private val gitSpecRepository: ProblemGitSpecRepository,
    private val mutationSpecRepository: ProblemMutationSpecRepository,
    private val mutantRepository: ProblemMutantRepository,
) {

    fun of(submission: Submission, problem: Problem): JudgeJobMessage = JudgeJobMessage.of(
        submission = submission,
        problem = problem,
        // 부분 점수 묶음 (#473). 없으면 빈 목록이고 채점은 지금까지와 같다.
        groups = groupRepository.findByProblemIdOrderByGroupNo(problem.id),
        sqlSpec = when (problem.problemKind) {
            ProblemKind.JUDGE_SQL -> sqlSpecRepository.findById(problem.id).orElse(null)
            else -> null
        },
        redisSpec = when (problem.problemKind) {
            ProblemKind.JUDGE_REDIS -> redisSpecRepository.findById(problem.id).orElse(null)
            else -> null
        },
        mutationSpec = when (problem.problemKind) {
            ProblemKind.JUDGE_MUTATION -> mutationSpecRepository.findById(problem.id).orElse(null)
                ?.let { JudgeMutationSpecMessage.of(it, mutantRepository.findByProblemIdOrderBySeqAsc(problem.id)) }
            else -> null
        },
        gitSpec = when (problem.problemKind) {
            ProblemKind.JUDGE_GIT -> gitSpecRepository.findById(problem.id).orElse(null)
            else -> null
        },
        regexSpec = when (problem.problemKind) {
            ProblemKind.JUDGE_REGEX -> regexSpecRepository.findById(problem.id).orElse(null)
            else -> null
        },
        mongoSpec = when (problem.problemKind) {
            ProblemKind.JUDGE_MONGODB -> mongoSpecRepository.findById(problem.id).orElse(null)
            else -> null
        },
    )
}
