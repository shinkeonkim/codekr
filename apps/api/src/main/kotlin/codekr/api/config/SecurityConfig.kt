package codekr.api.config

import codekr.api.auth.security.JwtAuthenticationFilter
import codekr.api.common.error.ErrorCode
import codekr.api.common.error.ErrorResponse
import codekr.api.config.properties.CorsProperties
import codekr.api.config.security.ApiAccessLevel
import codekr.api.config.security.ApiAccessScanner
import codekr.api.user.entity.UserRole
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val corsProperties: CorsProperties,
    private val objectMapper: ObjectMapper,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    /**
     * 역할 위계 (#103).
     *
     * SUPERUSER 는 ADMIN 이 할 수 있는 것을 모두 할 수 있고, ADMIN 은 각 담당 역할이
     * 할 수 있는 것을 모두 할 수 있다. 위계가 없으면 최고 관리자에게 모든 역할을 일일이
     * 붙여야 하고, 역할을 하나 늘릴 때마다 기존 계정을 전부 손봐야 한다.
     *
     * 반대 방향은 없다 — 문제 출제자가 인프라를 만질 수 없다는 것이 이 이슈의 목적이다.
     */
    @Bean
    fun roleHierarchy(): RoleHierarchy = RoleHierarchyImpl.withDefaultRolePrefix()
        .role(UserRole.SUPERUSER.name).implies(UserRole.ADMIN.name)
        .role(UserRole.ADMIN.name).implies(
            UserRole.PROBLEM_SETTER.name,
            UserRole.CONTEST_MANAGER.name,
            UserRole.BOARD_MANAGER.name,
        )
        .build()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // JWT 기반 무상태 API 다 — CSRF 토큰이 보호할 세션 쿠키가 없다.
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .authorizeHttpRequests { registry ->
                /*
                    인가 규칙을 **컨트롤러의 선언에서 끌어낸다** (#198).

                    전에는 여기에 경로 패턴을 손으로 적었다. 컨트롤러의 매핑과 이 목록과
                    시험의 목록이 각각 경로를 다시 적었고, **경로가 바뀌어도 셋 다
                    컴파일됐다.** 게다가 패턴은 순서에 의존해서, 새 규칙을 잘못된 자리에
                    끼우면 조용히 덮였다.

                    지금은 (메서드, 경로) 단위의 정확한 규칙이라 순서가 뜻을 갖지 않는다.
                    집행 지점은 그대로 여기다 — 본문 바인딩 **전에** 막아야 하기 때문이다.
                */
                for (rule in ApiAccessScanner.scan()) {
                    val matcher = registry.requestMatchers(HttpMethod.valueOf(rule.method), rule.pattern)
                    when (val level = rule.level) {
                        is ApiAccessLevel.Public -> matcher.permitAll()
                        is ApiAccessLevel.Authenticated -> matcher.authenticated()
                        is ApiAccessLevel.Role -> matcher.hasRole(level.role.name)
                    }
                }

                registry
                    // 컨트롤러가 아닌 것들. 스캔에 잡히지 않으므로 여기 남는다.
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    /*
                        Prometheus 는 토큰을 들고 다니지 않는다 (#676).

                        **관리 포트를 따로 두는 길(`management.server.port`)을 택하지 않았다.**
                        그쪽이 구조적으로는 낫지만 — 인그레스가 무엇을 하든 밖에서 못 닿는다 —
                        기동 프로브 세 개와 Service·containerPort 와 통합 시험의 포트가 함께
                        움직이고, 관리 컨텍스트에서 이 필터 체인이 어떻게 도는지를 따로
                        증명해야 한다. 지금 얻는 것에 비해 움직이는 것이 너무 많다.

                        대신 이 선택의 약점 — "인그레스에 `/actuator` 를 넣으면 조용히
                        열린다" — 을 시험으로 막았다: `scripts/check-ingress-paths.py` 가
                        인그레스가 `/actuator` 를 api 로 보내면 CI 를 실패시킨다.

                        **`metrics` 는 열지 않는다.** Prometheus 가 읽는 것은 이 하나뿐이고,
                        `env`·`configprops` 는 애초에 `exposure.include` 에 없다 (설정값이
                        그대로 보인다 — `docs/09_배포_가이드.md`).
                    */
                    .requestMatchers("/actuator/prometheus").permitAll()
                    // 웹소켓은 핸드셰이크 뒤 자체 인증을 한다 (#40).
                    .requestMatchers("/ws/**").permitAll()
                    /*
                        **선언되지 않은 것은 막는다.** 컨트롤러 핸들러는 스캐너가 기동에서
                        붙잡으므로 여기 오지 않는다. 여기 오는 것은 오류 페이지처럼
                        우리가 만들지 않은 경로다.
                    */
                    .anyRequest().authenticated()
            }
            .exceptionHandling { handling ->
                handling
                    .authenticationEntryPoint { _, response, _ -> writeError(response, ErrorCode.UNAUTHORIZED) }
                    .accessDeniedHandler { _, response, _ -> writeError(response, ErrorCode.FORBIDDEN) }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    private fun writeError(response: HttpServletResponse, code: ErrorCode) {
        response.status = code.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.writer.write(objectMapper.writeValueAsString(ErrorResponse.of(code)))
    }

    private fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = corsProperties.allowedOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type")
            allowCredentials = true
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", configuration) }
    }
}
