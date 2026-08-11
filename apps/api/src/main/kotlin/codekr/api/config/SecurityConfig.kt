package codekr.api.config

import codekr.api.auth.security.JwtAuthenticationFilter
import codekr.api.common.error.ErrorCode
import codekr.api.common.error.ErrorResponse
import codekr.api.config.properties.CorsProperties
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
                registry
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    .requestMatchers("/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/problems", "/api/v1/problems/*").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/runtimes").permitAll()
                    // 랭킹은 공개 정보다 (#57, #85).
                    .requestMatchers(HttpMethod.GET, "/api/v1/rankings", "/api/v1/rankings/*").permitAll()
                    // 대회 목록·상세는 공개다. 참가 등록만 로그인이 필요하다 (#61).
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/contests",
                        "/api/v1/contests/*",
                        // 순위표는 관전자도 본다 (#63).
                        "/api/v1/contests/*/scoreboard",
                    ).permitAll()
                    // 게시판 읽기는 공개다 (#137). 로그인해야 읽을 수 있으면
                    // 검색으로 들어온 사람이 아무것도 볼 수 없다.
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/posts",
                        "/api/v1/posts/*",
                        "/api/v1/posts/*/comments",
                    ).permitAll()
                    // 이미지는 공개다 (#115). 키에 내용 해시가 들어 있어 추측할 수 없다.
                    .requestMatchers(HttpMethod.GET, "/api/v1/files/**").permitAll()
                    // 링크 공유 문제집은 로그인 없이도 열린다 (#87).
                    .requestMatchers(HttpMethod.GET, "/api/v1/collections/shared/*").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    // 어드민 영역의 역할 규칙 (#103).
                    //
                    // 메서드 보안(@PreAuthorize)이 아니라 여기에 두는 이유:
                    //   1. 메서드 보안은 **본문 바인딩 뒤에** 돈다. 권한 없는 요청이
                    //      400(검증 실패)을 먼저 받아, 막혔다는 사실조차 알 수 없다
                    //   2. 인가가 두 곳에 흩어지면 새 컨트롤러에서 어느 쪽을 써야 하는지 헷갈린다
                    // 대회 운영은 대회 관리자 이상 (#61, #103).
                    .requestMatchers("/api/v1/admin/contests/**")
                    .hasRole(UserRole.CONTEST_MANAGER.name)
                    .requestMatchers("/api/v1/admin/problems/**")
                    .hasRole(UserRole.PROBLEM_SETTER.name)
                    .requestMatchers(
                        "/api/v1/admin/queues/**",
                        "/api/v1/admin/executors/**",
                        "/api/v1/admin/retention/**",
                    )
                    .hasRole(UserRole.ADMIN.name)
                    // **위에 적히지 않은 어드민 경로는 최고 관리자만.**
                    // 규칙을 적지 않고 어드민 API 를 늘리면 아무도 못 쓰게 되지,
                    // 모두가 쓸 수 있게 되지 않는다 (안전한 기본값).
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole(UserRole.SUPERUSER.name)
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
