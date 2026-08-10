package codekr.api.activity.controller

import codekr.api.activity.dto.ActivityResponse
import codekr.api.activity.service.ActivityService
import codekr.api.auth.security.AuthPrincipal
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/users/me/activity")
class ActivityController(private val activityService: ActivityService) {

    @GetMapping
    fun findMyActivity(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        principal: AuthPrincipal,
    ): ActivityResponse = activityService.findActivity(principal.userId, from, to)
}
