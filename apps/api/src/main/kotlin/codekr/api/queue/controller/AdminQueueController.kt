package codekr.api.queue.controller

import codekr.api.queue.dto.QueueStatusResponse
import codekr.api.queue.service.QueueMonitorService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 접근 제어는 SecurityConfig 의 admin 경로 규칙(hasRole("ADMIN"))이 담당한다. */
@RestController
@RequestMapping("/api/v1/admin/queues")
class AdminQueueController(private val queueMonitorService: QueueMonitorService) {

    @GetMapping
    fun status(): QueueStatusResponse = queueMonitorService.status()
}
