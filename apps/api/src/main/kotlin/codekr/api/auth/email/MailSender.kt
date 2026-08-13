package codekr.api.auth.email

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.stereotype.Component
import java.util.Properties

/**
 * 메일 설정 (#233).
 *
 * [host] 가 비어 있으면 **보내지 않는다.** 저장소(#115)와 같은 규칙이다 — 로컬에서
 * 메일 서버 없이 전체가 돌아야 하고, 발송이 안 된다고 서비스가 뜨지 못하면 안 된다.
 */
@ConfigurationProperties(prefix = "codekr.mail")
data class MailProperties(
    val host: String = "",
    val port: Int = 587,
    val username: String = "",
    val password: String = "",
    val from: String = "no-reply@xn--o22b4l.kr",
    /** 인증 링크가 가리킬 곳. 메일은 서버가 만들므로 주소를 스스로 알아야 한다. */
    val siteUrl: String = "http://localhost:13000",
) {
    val enabled: Boolean get() = host.isNotBlank()
}

/**
 * 메일 한 통 (#233).
 *
 * **직접 MTA 를 운영하지 않는다.** 발송 서비스의 SMTP 엔드포인트에 붙는다 — 스팸
 * 처리·바운스·평판 관리는 그쪽이 지고, 우리는 벤더 SDK 를 하나 더 들이지 않는다.
 */
@Component
class MailSender(private val properties: MailProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val delegate: JavaMailSenderImpl? = properties.takeIf { it.enabled }?.let {
        JavaMailSenderImpl().apply {
            host = it.host
            port = it.port
            username = it.username
            password = it.password
            javaMailProperties = Properties().apply {
                put("mail.smtp.auth", it.username.isNotBlank().toString())
                put("mail.smtp.starttls.enable", "true")
                // 붙지 않는 SMTP 서버에 무한정 매달리지 않는다. 발송은 곁가지 작업이다.
                put("mail.smtp.connectiontimeout", "5000")
                put("mail.smtp.timeout", "5000")
                put("mail.smtp.writetimeout", "5000")
            }
        }
    }

    /**
     * 보낸다. **실패해도 예외를 밖으로 내보내지 않는다** — 가입이 메일 때문에 실패하면
     * 안 된다. 대신 로그에 남긴다.
     *
     * 설정이 없으면 **본문을 로그로 찍는다.** 로컬 개발에서 인증 링크를 확인하는 길이
     * 그것뿐이고, 그러지 않으면 로컬에서는 인증 흐름을 아예 시험할 수 없다.
     */
    fun send(to: String, subject: String, body: String) {
        val sender = delegate ?: run {
            log.info("메일 설정이 없어 보내지 않습니다. to={} subject={}\n{}", to, subject, body)
            return
        }
        try {
            sender.send(
                SimpleMailMessage().apply {
                    setFrom(properties.from)
                    setTo(to)
                    setSubject(subject)
                    setText(body)
                },
            )
        } catch (e: Exception) {
            log.error("메일 발송 실패 to={} subject={}", to, subject, e)
        }
    }

    val enabled: Boolean get() = delegate != null
}
