package codekr.api.submission.dto

import codekr.api.submission.entity.Submission
import codekr.api.submission.entity.SubmissionStatus

data class SubmitResponse(val submissionId: Long, val status: SubmissionStatus) {
    companion object {
        fun from(submission: Submission) = SubmitResponse(submission.id, submission.status)
    }
}
