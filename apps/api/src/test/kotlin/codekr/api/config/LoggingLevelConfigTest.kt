package codekr.api.config

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.assertTrue

/**
 * `logging.level` 에 적힌 이름이 실제 패키지를 가리키는지 본다 (#686).
 *
 * **`kr.codekr: INFO` 가 오래 있었다.** 이 저장소의 패키지는 `codekr` 이라 그 이름은
 * 아무 로거에도 안 붙었다. 값이 기본값과 같아서 **아무 차이가 안 났고, 그래서 아무도
 * 몰랐다.** 드러나는 것은 누가 `DEBUG` 로 내려 보고 로그가 그대로일 때다 — 그때는
 * 설정을 의심하기 전에 코드를 한참 본다.
 *
 * 값이 아니라 **이름이 무언가를 가리키는지**만 본다. 레벨을 무엇으로 둘지는 판단이고,
 * 이름이 틀린 것은 사실이다.
 */
class LoggingLevelConfigTest {

    private val root: Path = Path.of("").toAbsolutePath()

    @Test
    fun `logging_level 의 이름이 실제 패키지를 가리킨다`() {
        val declared = loggerNames()
        assertTrue(declared.isNotEmpty(), "logging.level 항목을 하나도 못 읽었습니다. 설정 모양이 바뀌었나요?")

        /*
            **"우리 것" 을 `startsWith` 로 가르면 안 된다.**

            이 시험을 처음 쓸 때 그렇게 했는데, 정작 잡아야 할 `kr.codekr` 이
            `codekr` 로 시작하지 않아 **프레임워크 로거로 걸러져 그냥 통과했다.**
            틀린 이름일수록 접두사가 안 맞는다 — 그것이 틀렸다는 뜻이니까.

            그래서 이름 어딘가에 `codekr` 이 있으면 우리 것으로 본다.
        */
        val missing = declared
            .filter { it.contains("codekr", ignoreCase = true) }
            .filterNot { root.resolve("src/main/kotlin/${it.replace('.', '/')}").exists() }

        assertTrue(missing.isEmpty(), "가리키는 패키지가 없는 로거 이름: $missing")
    }

    /** `logging.level:` 블록의 `이름: 레벨` 줄만 읽는다. 주석과 다른 블록은 건너뛴다. */
    private fun loggerNames(): List<String> {
        val lines = root.resolve("src/main/resources/application.yml").readText().lines()
        val start = lines.indexOfFirst { it.trimEnd() == "  level:" }
        if (start < 0) return emptyList()

        return lines.drop(start + 1)
            .takeWhile { it.isBlank() || it.startsWith("    ") }
            .map { it.substringBefore('#').trim() }
            .filter { it.contains(':') && !it.startsWith("#") }
            .map { it.substringBefore(':').trim() }
            .filter { it.isNotEmpty() }
    }
}
