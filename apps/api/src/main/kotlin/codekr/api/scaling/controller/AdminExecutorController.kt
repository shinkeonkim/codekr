package codekr.api.scaling.controller

import codekr.api.scaling.dto.ExecutorScaleStatus
import codekr.api.scaling.dto.ScaleRequest
import codekr.api.scaling.service.ExecutorScaleService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 접근 제어는 SecurityConfig 의 admin 경로 규칙(hasRole("ADMIN"))이 담당한다. */
@RestController
@RequestMapping("/api/v1/admin/executors")
class AdminExecutorController(private val scaleService: ExecutorScaleService) {

    @GetMapping
    fun status(): ExecutorScaleStatus = scaleService.status()

    @PostMapping("/scale")
    fun scale(@Valid @RequestBody request: ScaleRequest): ExecutorScaleStatus =
        scaleService.scale(request.replicas)
}
