package codekr.api.ranking.service

import codekr.api.ranking.repository.ProblemScoreResyncRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 문제가 바뀌면 그 문제에 걸린 점수를 따라 움직인다 (#194).
 *
 * 점수 계산식은 처음부터 **맞힌 시점이 아니라 지금의 난이도**를 쓰도록 적혀 있었다.
 * 그런데 그 식을 다시 돌리는 경로가 제출과 어드민의 수동 재계산뿐이라, 난이도를 올리면
 * **그 뒤에 푼 사람만** 오른 점수를 받았다. 같은 문제를 푼 두 사람의 점수가 푼 시점
 * 때문에 갈렸다.
 *
 * ## 왜 저장과 같은 트랜잭션에서 하는가
 *
 * 이슈에서는 뒤에서 처리하는 쪽(이벤트·비동기)을 기울여 봤다. 사람 수만큼 도는 것을
 * 전제했기 때문이다. 여기서는 **문장 세 개**로 끝나므로 그 전제가 성립하지 않는다 —
 * 몇 명이 풀었든 비용이 같고, 인덱스가 걸린 컬럼(`problem_id`)으로만 훑는다.
 *
 * 같은 트랜잭션이면 얻는 것이 하나 더 있다. 저장이 실패했는데 점수만 바뀌어 있는 상태가
 * 아예 생기지 않고, 어드민은 **저장 버튼을 놓은 순간 반영이 끝났다**고 믿을 수 있다.
 * 뒤에서 처리했다면 "언제 반영되는지" 를 화면이 설명해야 했을 것이다.
 */
@Service
class ProblemScoreResyncService(private val resyncRepository: ProblemScoreResyncRepository) {

    /**
     * 그 문제의 점수 행을 지금 상태에 맞춘다.
     *
     * 난이도뿐 아니라 공개 여부와 삭제도 같은 문제였다. 계산식이 `published = true` 와
     * `deleted_at IS NULL` 을 보는데, 그 조건을 다시 확인하는 경로가 없었으므로
     * **비공개로 돌린 문제의 점수가 계속 남아 있었다.**
     *
     * @return 점수가 실제로 움직인 사용자 수.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun resync(problemId: Long): Int {
        // 순서가 중요하다. 먼저 지워야 자격을 잃은 행이 최고 점수 계산에 섞이지 않는다.
        val removed = resyncRepository.deleteDisqualified(problemId)
        val changed = resyncRepository.upsertQualified(problemId)

        val affected = (removed + changed).toSet()
        resyncRepository.raisePeakScores(affected)
        return affected.size
    }
}
