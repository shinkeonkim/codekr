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

    /**
     * 사람이 넣지 않은 파일들 (#594).
     *
     * **조용히 버려도 되는 유일한 것들이다.** "모르는 파일을 버리지 않는다" 는 규칙은
     * 출제자가 넣은 것이 들어갔다고 **믿게 두지 않으려는** 것인데, 이것들은 출제자가
     * 넣은 적이 없다 — macOS 는 폴더를 열기만 해도 `.DS_Store` 를 만들고, Finder 로
     * 압축하면 `__MACOSX/`·`._*` 가 함께 들어간다.
     *
     * **목록을 좁게 둔다.** 정확히 아는 이름만 무시하고 나머지는 그대로 거절한다.
     */
    private fun isJunk(name: String): Boolean {
        val last = name.substringAfterLast('/')
        return name.startsWith("__MACOSX/") ||
            last == ".DS_Store" ||
            last == "Thumbs.db" ||
            last.startsWith("._")
    }

    /**
     * 맨 위 폴더 하나를 벗긴다 (#594).
     *
     * `zip -r bundle.zip 08-sql-seoul-members` 는 이름을 `08-sql-seoul-members/...` 로
     * 만든다 — **폴더를 압축하는 가장 자연스러운 방법**이고, 그러면 루트에 `problem.json`
     * 이 없어 통째로 거절당했다.
     *
     * **이것은 조용히 고치는 것이 아니다.** 폴더 접두사는 내용이 아니라 **포장**이라,
     * 벗겨도 무엇이 들어왔는지는 그대로다. 다만 맨 위가 둘 이상이면 벗기지 않는다 —
     * 그때는 무엇이 뜻인지 알 수 없고, 짐작하면 엉뚱한 것을 문제로 만든다.
     */
    private fun stripWrapper(names: Collection<String>): String {
        val roots = names.map { it.substringBefore('/') }.toSet()
        if (roots.size != 1) return ""
        val root = roots.first()
        // 루트에 파일이 하나라도 있으면(=`problem.json`) 벗길 폴더가 아니다.
        if (names.any { it == root }) return ""
        return "$root/"
    }

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
        /**
         * 테스트케이스도 `problem.json` 도 아닌 파일들. 경로 → 내용 (#561).
         *
         * **여기서 거절하지 않는다.** `problem.json` 이 가리키는 파일인지는 그것을 읽어
         * 봐야 알 수 있는데, zip 안의 순서는 만든 쪽이 정하므로 스키마 파일이 먼저 나올
         * 수 있다. 그래서 **모아 두고, 아무도 안 가리키면 그때 거절한다** —
         * "모르는 파일은 조용히 버리지 않는다" 는 규칙은 그대로다.
         */
        val extras: Map<String, String> = emptyMap(),
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
        // **먼저 다 읽고 나서 분류한다** (#594). 맨 위 폴더를 벗기려면 이름이 전부
        // 모여야 한다 — 하나씩 보는 동안에는 그 폴더가 포장인지 자료인지 알 수 없다.
        // 크기·개수 상한은 읽는 동안 그대로 센다.
        val files = linkedMapOf<String, String>()
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
                if (!isJunk(name)) files[name] = body.toString(Charsets.UTF_8)
            }
        }

        val prefix = stripWrapper(files.keys)
        var meta: String? = null
        val inputs = mutableMapOf<Int, String>()
        val outputs = mutableMapOf<Int, String>()
        val extras = mutableMapOf<String, String>()
        for ((raw, text) in files) {
            val name = raw.removePrefix(prefix)
            when {
                name == META_ENTRY -> meta = text
                name.startsWith(TESTCASE_DIR) && name.endsWith(".in") ->
                    inputs[seqOf(name, ".in")] = text
                name.startsWith(TESTCASE_DIR) && name.endsWith(".out") ->
                    outputs[seqOf(name, ".out")] = text
                // 나머지는 모아 둔다. `problem.json` 이 가리키는 것일 수 있고
                // (SQL 스키마 — #561), 그 판단은 메타를 읽어야 할 수 있다.
                // **아무도 안 가리키면 호출자가 거절한다.**
                else -> extras[name] = text
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
            extras,
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
