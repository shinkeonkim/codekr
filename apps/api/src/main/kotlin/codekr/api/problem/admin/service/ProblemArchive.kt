package codekr.api.problem.admin.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 문제 묶음 파일을 푼다 (#479, #537).
 *
 * ## 왜 zip 인가
 *
 * 테스트케이스가 천 개면 JSON 본문에 담을 수 없다 — 파일이 수십 MB가 되고, 편집기로
 * 열리지 않고, 한 글자를 고치려고 전체를 다시 올려야 한다. 줄바꿈과 인코딩이 JSON
 * 문자열 안에서 뭉개지는 것은 #313 이 SQL 스키마에서 이미 겪은 일이고, 그때도 답은
 * **별도 파일로 빼는 것**이었다.
 *
 * ```
 * problem.json        시드(`scripts/seed-problems` 의 json 파일)와 같은 형식
 * testcases/1.in      seq 가 파일 이름이다
 * testcases/1.out
 * ```
 *
 * ## 맨 JSON 도 받는다 (#537)
 *
 * **우리가 가진 파일은 대부분 zip 이 아니다.** `scripts/seed-problems` 의 18개도,
 * 스킬(#480~#482)이 내놓는 것도 전부 맨 JSON 이다. 테스트케이스가 세 개인 문제까지
 * 압축하게 하면 그 단계에서 얻는 것이 없다.
 *
 * 형식을 새로 만드는 것이 아니다. 묶음 규칙이 이미 **"묶음에 테스트케이스 파일이 있으면
 * 그쪽이 이기고, 없으면 `problem.json` 의 `testcases` 를 쓴다"** 이므로,
 * 맨 JSON 은 **테스트케이스 파일이 없는 묶음**이다.
 *
 * **무엇으로 구분하는가 — 매직 바이트다.** 파일 이름과 `Content-Type` 은 올리는 쪽이
 * 정하는 값이라 믿지 않는다. `.json` 이라 적힌 zip 도, 확장자가 없는 JSON 도 들어온다.
 *
 * ## 압축을 푸는 일은 안전한 작업이 아니다
 *
 * 이것은 선택이 아니라 필수다.
 *
 * - **경로 탈출** — `../../etc/passwd` 를 담은 zip 이 있다. 우리는 파일을 쓰지 않지만,
 *   이름을 그대로 믿으면 `testcases/` 밖의 것을 테스트케이스로 읽게 된다
 * - **압축 폭탄** — 몇 KB 가 몇 GB 로 풀린다. 그래서 **푼 크기와 파일 수**를 세면서 풀고,
 *   넘으면 그 자리에서 멈춘다. 다 풀고 나서 재면 이미 늦다
 *
 * ## 원본은 남지 않는다
 *
 * **어디에도 저장하지 않는다.** 받은 스트림을 그대로 풀어 문제 데이터로 옮기고 끝낸다 —
 * 저장했다가 지우는 방식은 "지우는 것을 잊는" 자리를 만들고, 그러면 저장소가
 * `문제 수 × 테스트케이스 크기 × 2` 로 늘어난다 (#424).
 */
object ProblemArchive {

    const val META_ENTRY = "problem.json"
    private const val TESTCASE_DIR = "testcases/"

    /** 푼 뒤의 총 크기 상한. 압축 폭탄을 여기서 끊는다. */
    private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024

    /** 파일 수 상한. 테스트케이스 하나에 두 개(`.in`/`.out`)이므로 넉넉히 잡는다. */
    private const val MAX_ENTRIES = 5_000

    /** zip 의 첫 네 바이트. 이름과 `Content-Type` 대신 이것으로 본다 (#537). */
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    /** 무엇으로 들어왔는지. 화면이 "zip 으로 읽었다"를 보여줄 수 있어야 한다. */
    enum class Source { ZIP, JSON }

    data class Content(
        /** `problem.json` 의 내용. */
        val meta: String,
        /** seq → (입력, 기대 출력). 맨 JSON 이면 비어 있다. */
        val testcases: Map<Int, Pair<String, String>>,
        val source: Source,
    )

    fun read(stream: InputStream): Content {
        val buffered = stream.buffered()
        return if (looksLikeZip(buffered)) readZip(buffered) else readJson(buffered)
    }

    /**
     * 앞 네 바이트만 보고 되돌린다.
     *
     * 파일을 다 읽어 놓고 판단하지 않는다 — 그러면 상한을 넘는 파일도 일단 메모리에
     * 올린 뒤에야 거절하게 된다. 압축 폭탄을 푸는 동안 재는 것과 같은 이유다.
     */
    private fun looksLikeZip(stream: BufferedInputStream): Boolean {
        stream.mark(ZIP_MAGIC.size)
        val head = ByteArray(ZIP_MAGIC.size)
        var read = 0
        while (read < head.size) {
            val count = stream.read(head, read, head.size - read)
            if (count < 0) break
            read += count
        }
        stream.reset()
        return read == head.size && head.contentEquals(ZIP_MAGIC)
    }

    /**
     * 맨 JSON. 통째로 `problem.json` 이다.
     *
     * **여기에도 상한을 건다.** zip 은 푸는 동안 재지만 맨 JSON 은 풀 것이 없어서
     * 그 검사를 지나지 않는다. 인라인 `testcases` 를 담은 JSON 은 압축되지 않은 만큼
     * 오히려 크다.
     */
    private fun readJson(stream: InputStream): Content {
        val body = stream.readNBytes((MAX_TOTAL_BYTES + 1).toInt())
        if (body.size > MAX_TOTAL_BYTES) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "파일이 너무 큽니다(최대 64MB).")
        }
        if (body.isEmpty()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "빈 파일입니다.")
        }
        return Content(body.toString(Charsets.UTF_8), emptyMap(), Source.JSON)
    }

    private fun readZip(stream: InputStream): Content {
        var meta: String? = null
        val inputs = mutableMapOf<Int, String>()
        val outputs = mutableMapOf<Int, String>()
        var total = 0L
        var entries = 0

        ZipInputStream(stream.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                if (++entries > MAX_ENTRIES) {
                    throw ApiException(ErrorCode.VALIDATION_ERROR, "파일이 너무 많습니다(최대 $MAX_ENTRIES 개).")
                }
                val name = safeName(entry.name)
                val body = zip.readNBytes((MAX_TOTAL_BYTES - total + 1).toInt())
                total += body.size
                if (total > MAX_TOTAL_BYTES) {
                    // **다 풀고 나서 재지 않는다.** 그때는 이미 메모리를 다 먹은 뒤다.
                    throw ApiException(ErrorCode.VALIDATION_ERROR, "압축을 푼 크기가 너무 큽니다(최대 64MB).")
                }
                val text = body.toString(Charsets.UTF_8)

                when {
                    name == META_ENTRY -> meta = text
                    name.startsWith(TESTCASE_DIR) && name.endsWith(".in") ->
                        inputs[seqOf(name, ".in")] = text
                    name.startsWith(TESTCASE_DIR) && name.endsWith(".out") ->
                        outputs[seqOf(name, ".out")] = text
                    // 모르는 파일은 **조용히 버리지 않고** 거절한다. 버리면 출제자는
                    // 자기가 넣은 것이 들어갔다고 믿는다.
                    else -> throw ApiException(ErrorCode.VALIDATION_ERROR, "묶음에 모르는 파일이 있습니다: $name")
                }
            }
        }

        val body = meta ?: throw ApiException(ErrorCode.VALIDATION_ERROR, "묶음에 $META_ENTRY 이 없습니다.")
        val onlyInput = inputs.keys - outputs.keys
        val onlyOutput = outputs.keys - inputs.keys
        if (onlyInput.isNotEmpty() || onlyOutput.isNotEmpty()) {
            // **짝이 없는 것은 테스트케이스가 아니다.** 입력만 있으면 정답이 없고,
            // 기대 출력만 있으면 무엇으로 그 답이 나오는지가 없다.
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "짝이 맞지 않는 테스트케이스가 있습니다: " +
                    (onlyInput.map { "$it.in" } + onlyOutput.map { "$it.out" }).sorted().joinToString(),
            )
        }

        return Content(
            body,
            inputs.keys.sorted().associateWith { inputs.getValue(it) to outputs.getValue(it) },
            Source.ZIP,
        )
    }

    /**
     * 이름을 믿지 않는다.
     *
     * zip 안의 이름은 만든 쪽이 정하는 값이고, `../` 나 절대 경로가 들어올 수 있다.
     * 윈도에서 만든 묶음은 `\` 를 쓰기도 한다.
     */
    private fun safeName(raw: String): String {
        val name = raw.replace('\\', '/')
        if (name.startsWith("/") || name.split("/").any { it == ".." }) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "묶음에 쓸 수 없는 경로가 있습니다: $raw")
        }
        return name
    }

    private fun seqOf(name: String, suffix: String): Int =
        name.removePrefix(TESTCASE_DIR).removeSuffix(suffix).toIntOrNull()
            ?: throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "테스트케이스 이름은 번호여야 합니다(예: testcases/1$suffix): $name",
            )
}
