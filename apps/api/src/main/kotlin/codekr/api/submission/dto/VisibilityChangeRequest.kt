package codekr.api.submission.dto

import codekr.api.submission.entity.SubmissionVisibility

/** 제출 후 공개 범위를 바꾼다. 작성자만 할 수 있다. */
data class VisibilityChangeRequest(val visibility: SubmissionVisibility)
