package codekr.api.queue.message

import codekr.api.problem.entity.ProblemTestcaseGroup

/** 부분 점수 묶음 (#473). **묶음 안을 다 맞혀야 그 점수를 받는다.** */
data class JudgeTestcaseGroupMessage(val groupNo: Int, val score: Int) {
    companion object {
        fun from(group: ProblemTestcaseGroup) = JudgeTestcaseGroupMessage(group.groupNo, group.score)
    }
}
