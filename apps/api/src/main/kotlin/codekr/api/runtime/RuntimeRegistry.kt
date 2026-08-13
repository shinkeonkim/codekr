package codekr.api.runtime

import codekr.api.common.error.ApiException
import codekr.api.problem.entity.ProblemKind
import codekr.api.common.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.stereotype.Component
import org.yaml.snakeyaml.Yaml

/**
 * 지원 언어/버전 목록을 YAML 에서 읽는다. 정의는 실행기와 공유하는 단일 파일이며
 * (`infra/runtimes/runtimes.yaml`), 컨테이너에서는 `/etc/codekr/runtimes.yaml` 로 들어온다.
 */
@Component
class RuntimeRegistry(
    @Value("\${codekr.runtimes.locations}") private val locations: List<String>,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val resourceLoader = DefaultResourceLoader()

    private val runtimes: List<RuntimeDefinition> by lazy { load() }
    private val byId: Map<String, RuntimeDefinition> by lazy { runtimes.associateBy { it.id } }

    fun findAll(): List<RuntimeDefinition> = runtimes

    /** 그 유형으로 풀 수 있는 런타임만 (#60). */
    fun findFor(kind: ProblemKind): List<RuntimeDefinition> = runtimes.filter { it.problemKind == kind }

    fun require(id: String): RuntimeDefinition = byId[id] ?: throw ApiException(ErrorCode.RUNTIME_NOT_FOUND)

    fun exists(id: String): Boolean = byId.containsKey(id)

    private fun load(): List<RuntimeDefinition> {
        val resource = locations.map(resourceLoader::getResource).firstOrNull { it.exists() }
            ?: error("런타임 정의 파일을 찾을 수 없습니다. 확인한 위치: $locations")

        log.info("런타임 정의 로드: {}", resource.description)
        val root = resource.inputStream.use { Yaml().load<Map<String, Any>>(it) }

        @Suppress("UNCHECKED_CAST")
        val entries = root["runtimes"] as? List<Map<String, Any>>
            ?: error("런타임 정의 파일에 runtimes 목록이 없습니다.")

        return entries.map {
            RuntimeDefinition(
                id = it.getValue("id") as String,
                label = it.getValue("label") as String,
                monacoLanguage = it.getValue("monacoLanguage") as String,
                template = (it["template"] as? String).orEmpty(),
                // 적지 않으면 stdin/stdout 이다 — 지금까지의 모든 런타임이 그것이다.
                problemKind = (it["problemKind"] as? String)
                    ?.let(ProblemKind::valueOf) ?: ProblemKind.JUDGE_STDIO,
                // 적지 않으면 0 이다 — 대부분의 런타임은 기동이랄 것이 없다 (#454).
                startupMs = (it["startupMs"] as? Int) ?: 0,
            )
        }
    }
}
