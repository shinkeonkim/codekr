package codekr.api.scaling.dto

import jakarta.validation.constraints.Min

data class ScaleRequest(@field:Min(0) val replicas: Int)
