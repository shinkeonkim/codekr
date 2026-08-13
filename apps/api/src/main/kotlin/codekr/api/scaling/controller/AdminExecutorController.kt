package codekr.api.scaling.controller

import codekr.api.auth.security.AuthPrincipal
import codekr.api.config.security.AdminApi
import codekr.api.scaling.dto.ExecutorScaleStatus
import codekr.api.scaling.dto.ScaleRequest
import codekr.api.scaling.dto.WorkerRequest
import codekr.api.scaling.service.ExecutorScaleService
import codekr.api.user.entity.UserRole
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 채점 파이프라인 워크로드 조정 (#40, #390).
 *
 * **이름이 실행기가 아니라 워크로드다.** 전에는 `/admin/executors` 였는데 조정할 수
 * 있는 것이 실행기 하나뿐이었기 때문이다 — 채점기가 큐를 못 빼면 실행기를 늘려도
 * 소용없는데, 그쪽은 `kubectl` 로 만져야 했다.
 *
 * **경로의 이름으로 아무 배포나 만질 수는 없다.** 설정의 허용 목록(`codekr.scaling.targets`)에
 * 있는 것만 조정된다 — 일반화의 편의와 안전을 둘 다 가지는 방법이다.
 */
@RestController
@RequestMapping("/api/v1/admin/workloads")
class AdminExecutorController(private val scaleService: ExecutorScaleService) {

    /** 조정할 수 있는 것 전부. 화면이 무엇이 있는지 서버에게 묻는다. */
    @AdminApi(UserRole.ADMIN)
    @GetMapping
    fun statuses(): List<ExecutorScaleStatus> = scaleService.statuses()

    /** 파드 수 — **처리량과 격리에 듣는다.** */
    @AdminApi(UserRole.ADMIN)
    @PostMapping("/{key}/scale")
    fun scale(
        @PathVariable key: String,
        @Valid @RequestBody request: ScaleRequest,
        principal: AuthPrincipal,
    ): ExecutorScaleStatus = scaleService.scale(principal.userId, key, request.replicas)

    /**
     * 워커 수 — **대기 시간에 듣는다** (#390).
     *
     * 재시작하지 않는다. 채점기가 이 값을 주기적으로 읽어 스스로 맞춘다.
     */
    @AdminApi(UserRole.ADMIN)
    @PostMapping("/{key}/workers")
    fun workers(
        @PathVariable key: String,
        @Valid @RequestBody request: WorkerRequest,
        principal: AuthPrincipal,
    ): ExecutorScaleStatus = scaleService.setWorkers(principal.userId, key, request.workers)
}
