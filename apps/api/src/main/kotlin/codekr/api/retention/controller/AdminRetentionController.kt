package codekr.api.retention.controller

import codekr.api.retention.dto.RetentionReport
import codekr.api.retention.service.RetentionService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 정리 배치를 수동으로 돌린다. 접근 제어는 SecurityConfig 의 admin 경로 규칙이 담당한다.
 *
 * 자동 실행(새벽 4시)을 기다리지 않고 결과를 확인해야 할 때 쓴다.
 */
@RestController
@RequestMapping("/api/v1/admin/retention")
class AdminRetentionController(private val retentionService: RetentionService) {

    @PostMapping("/cleanup")
    fun cleanup(): RetentionReport = retentionService.cleanup()
}
