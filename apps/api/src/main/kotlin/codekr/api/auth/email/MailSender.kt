package codekr.api.auth.email

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.event.EventListener
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.stereotype.Component
import java.util.Properties

/**
 * 메일 설정 (#233, #355).
 *
 * [host] 가 비어 있으면 **보내지 않는다.** 저장소(#115)와 같은 규칙이다 — 로컬에서
 * 메일 서버 없이 전체가 돌아야 하고, 발송이 안 된다고 서비스가 뜨지 못하면 안 된다.
 */
@ConfigurationProperties(prefix = "codekr.mail")
data class MailProperties(
    val host: String = "",
    val port: Int = 587,
    /**
     * 붙자마자 TLS 를 하는가 (implicit TLS, #355).
     *
     * **465 는 STARTTLS 로는 붙지 못한다.** 평문 EHLO 를 보내는데 서버는 핸드셰이크를
     * 기다리므로, 타임아웃까지 매달렸다가 실패한다.
     *
     * **포트에서 유추하지 않고 설정으로 뺐다.** 유추는 비표준 포트를 쓰는 서비스에서
     * 틀린다. 대신 잘못 넣으면 조용히 실패하므로, 기동할 때 [MailSender] 가
     * 어긋난 조합을 짚어 준다.
     */
    val ssl: Boolean = false,
    val username: String = "",
    val password: String = "",
    /**
     * 발신 주소.
     *
     * **인증 계정과 같아야 한다** (#355). 다른 주소로 보내면 대개 제공자가 거부하고,
     * 통과하더라도 SPF/DKIM 정렬이 깨져 스팸함으로 간다. "어느 주소로 보이고 싶은가"
     * 가 아니라 **"어느 주소로 보낼 수 있는가"** 의 문제다.
     */
    val from: String = "no-reply@xn--hy1by51c.kr",
    /** 인증 링크가 가리킬 곳. 메일은 서버가 만들므로 주소를 스스로 알아야 한다. */
    val siteUrl: String = "http://localhost:13000",
) {
    val enabled: Boolean get() = host.isNotBlank()
}

/**
 * 한 통을 보낸 결과 (#524).
 *
 * **밖으로 예외를 내보내지 않는 것과, 결과를 감추는 것은 다르다.** 가입은 메일이
 * 실패해도 끝나야 하지만(그 판단은 그대로다), **어드민이 손으로 다시 보낼 때**는
 * 갔는지 아닌지를 알아야 한다 — 모르면 될 때까지 다시 누르는 수밖에 없다.
 */
enum class MailOutcome {
    /** 발송 서버가 받아 갔다. 받는 쪽 사서함에 닿았는지까지는 알 수 없다. */
    SENT,

    /** 메일 설정이 없다. 로컬에서는 정상이고, 본문은 로그에 남는다. */
    SKIPPED,

    /** 붙지 못했거나 거절당했다. 원인은 로그에 있다 — 응답에 싣지 않는다. */
    FAILED,
}

/**
 * 메일 한 통 (#233, #355).
 *
 * **직접 MTA 를 운영하지 않는다.** 발송 서비스의 SMTP 엔드포인트에 붙는다 — 스팸
 * 처리·바운스·평판 관리는 그쪽이 지고, 우리는 벤더 SDK 를 하나 더 들이지 않는다.
 * 갈아탈 때 값만 바꾸면 되는 것도 이 방식의 값이다.
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
                if (it.ssl) {
                    // 붙자마자 TLS (465). STARTTLS 와 **함께 켤 수 없다** — 둘은 다른 절차다.
                    put("mail.smtp.ssl.enable", "true")
                } else {
                    put("mail.smtp.starttls.enable", "true")
                }
                // 붙지 않는 SMTP 서버에 무한정 매달리지 않는다. 발송은 곁가지 작업이다.
                put("mail.smtp.connectiontimeout", "5000")
                put("mail.smtp.timeout", "5000")
                put("mail.smtp.writetimeout", "5000")
            }
        }
    }

    /**
     * 기동할 때 한 번 붙어 본다 (#355).
     *
     * **켜 놓고도 안 되는 것을 모르는 상태가 가장 나쁘다.** 발송은 예외를 삼키므로
     * (그 판단은 맞다 — 가입이 메일 때문에 실패하면 안 된다), 아무도 가입하지 않는
     * 동안에는 고장을 알 길이 없다.
     *
     * **기동을 막지는 않는다.** 메일 서버가 죽었다고 서비스가 뜨지 못하면 그것이 더
     * 큰 고장이다. 기동이 끝난 뒤(`ApplicationReadyEvent`)에 확인만 한다.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun checkConnection() {
        val sender = delegate ?: run {
            log.info("메일 설정이 없습니다. 인증 메일을 보내지 않고 로그로만 남깁니다.")
            return
        }
        if (properties.port == SSL_PORT && !properties.ssl) {
            // 이 조합은 **반드시** 실패한다. 값 하나가 빠졌다는 것을 짚어 준다.
            log.warn("메일: {}번 포트는 붙자마자 TLS 입니다. codekr.mail.ssl 을 켜야 합니다.", SSL_PORT)
        }
        if (properties.port == STARTTLS_PORT && properties.ssl) {
            log.warn("메일: {}번 포트는 보통 STARTTLS 입니다. codekr.mail.ssl 이 켜져 있습니다.", STARTTLS_PORT)
        }
        runCatching { sender.testConnection() }
            .onSuccess { log.info("메일 준비됨 host={} port={} ssl={}", properties.host, properties.port, properties.ssl) }
            .onFailure { log.error("메일 서버에 붙지 못했습니다 host={} port={} 원인={}", properties.host, properties.port, scrub(it)) }
    }

    /**
     * 보낸다. **실패해도 예외를 밖으로 내보내지 않는다** — 가입이 메일 때문에 실패하면
     * 안 된다. 대신 로그에 남긴다.
     *
     * 설정이 없으면 **본문을 로그로 찍는다.** 로컬 개발에서 인증 링크를 확인하는 길이
     * 그것뿐이고, 그러지 않으면 로컬에서는 인증 흐름을 아예 시험할 수 없다.
     */
    fun send(to: String, subject: String, body: String): MailOutcome {
        val sender = delegate ?: run {
            log.info("메일 설정이 없어 보내지 않습니다. to={} subject={}\n{}", to, subject, body)
            return MailOutcome.SKIPPED
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
            return MailOutcome.SENT
        } catch (e: Exception) {
            /*
              **스택트레이스를 통째로 남기지 않는다** (#355).

              JavaMail 의 예외는 서버가 돌려준 문장을 그대로 싣는데, 인증 실패 응답에
              계정 이름이 섞이는 제공자가 있다. 로그는 사람이 많이 보는 곳이고,
              한번 찍힌 것은 되돌릴 수 없다.
            */
            log.error("메일 발송 실패 to={} subject={} 원인={}", to, subject, scrub(e))
            return MailOutcome.FAILED
        }
    }

    /**
     * 로그에 남길 원인 한 줄.
     *
     * 자격증명이 섞여 있으면 지운다. **"안 섞일 것이다" 로 두지 않는다** — 섞이는지는
     * 제공자가 정하고, 우리는 그것을 통제하지 못한다.
     */
    internal fun scrub(e: Throwable): String =
        listOfNotNull(properties.password.takeIf { it.isNotBlank() }, properties.username.takeIf { it.isNotBlank() })
            .fold("${e.javaClass.simpleName}: ${e.message}") { message, secret -> message.replace(secret, "***") }

    val enabled: Boolean get() = delegate != null

    /** 시험이 **실제로 나가는 설정**을 볼 수 있게 연다 (#355). 465 는 눈으로 못 잡는다. */
    internal val smtpProperties: Properties get() = delegate?.javaMailProperties ?: Properties()

    private companion object {
        const val SSL_PORT = 465
        const val STARTTLS_PORT = 587
    }
}
