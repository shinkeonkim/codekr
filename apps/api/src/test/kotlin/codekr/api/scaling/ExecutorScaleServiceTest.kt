package codekr.api.scaling

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.audit.service.AdminAuditService
import codekr.api.config.properties.ScalingProperties
import codekr.api.config.properties.ScalingTarget
import codekr.api.scaling.dto.ExecutorScaleState
import codekr.api.scaling.service.ExecutorScaleClient
import codekr.api.scaling.service.ExecutorScaleService
import codekr.api.scaling.service.JudgeWorkerRegistry
import codekr.api.scaling.service.ScaleAccessException
import codekr.api.scaling.service.ScaleAccessFailure
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class StubScaleClient(
    override val available: Boolean = true,
    override val unavailableReason: String? = null,
    override val namespace: String? = "codekr-exec",
    private var desired: Int = 2,
    private var ready: Int = 2,
    private val readError: Throwable? = null,
) : ExecutorScaleClient {
    var scaledTo: Int? = null

    /** 어느 네임스페이스로 불렸는지 (#390). 대상마다 다를 수 있다. */
    var readNamespace: String? = null

    override fun read(deployment: String, namespaceOverride: String?): Pair<Int, Int> {
        readNamespace = namespaceOverride
        readError?.let { throw it }
        return desired to ready
    }

    override fun scale(deployment: String, replicas: Int, namespaceOverride: String?) {
        scaledTo = replicas
        desired = replicas
    }
}

/** 워커 수를 어디에 적었는지만 본다 — Redis 를 띄우지 않고 확인할 수 있어야 한다. */
private class StubWorkers : JudgeWorkerRegistry {
    val written = mutableMapOf<String, Int>()
    override fun read(lane: String): Int? = written[lane]
    override fun write(lane: String, workers: Int) {
        written[lane] = workers
    }
}


private const val EXECUTOR = "executor"
private const val JUDGE = "judge"

class ExecutorScaleServiceTest {

    private val properties = ScalingProperties(
        targets = mapOf(
            "executor" to ScalingTarget(
                label = "실행기",
                deployment = "codekr-executor",
                namespace = "codekr-exec",
                minReplicas = 1,
                maxReplicas = 10,
            ),
            "judge" to ScalingTarget(label = "채점기", deployment = "codekr-judge", lane = "general"),
        ),
    )

    private fun service(client: ExecutorScaleClient) =
        ExecutorScaleService(client, properties, StubWorkers(), org.mockito.Mockito.mock(AdminAuditService::class.java))

    @Test
    fun `클러스터 밖에서는 사용 불가 상태를 돌려준다`() {
        val service = service(StubScaleClient(available = false, unavailableReason = "클러스터 밖"))

        val status = service.status(EXECUTOR)

        assertEquals(ExecutorScaleState.OUTSIDE_CLUSTER, status.state)
        assertEquals("클러스터 밖", status.reason)
    }

    @Test
    fun `클러스터 밖에서 조정을 시도하면 거부한다`() {
        val service = service(StubScaleClient(available = false))

        val error = runCatching { service.scale(0, EXECUTOR, 3) }.exceptionOrNull()

        assertTrue(error is ApiException && error.errorCode == ErrorCode.SCALING_UNAVAILABLE)
    }

    @Test
    fun `현재 replica 수를 읽는다`() {
        val service = service(StubScaleClient(desired = 4, ready = 3))

        val status = service.status(EXECUTOR)

        assertEquals(ExecutorScaleState.OK, status.state)
        assertEquals(4, status.desiredReplicas)
        assertEquals(3, status.readyReplicas)
    }

    @Test
    fun `허용 범위 안이면 조정한다`() {
        val client = StubScaleClient()
        val service = service(client)

        service.scale(0, EXECUTOR, 5)

        assertEquals(5, client.scaledTo)
    }

    @Test
    fun `허용 범위를 벗어나면 거부한다`() {
        // 실수로 큰 수를 넣어 노드를 마비시키는 일을 막는다.
        val client = StubScaleClient()
        val service = service(client)

        listOf(0, 11, 200).forEach { replicas ->
            val error = runCatching { service.scale(0, EXECUTOR, replicas) }.exceptionOrNull()
            assertTrue(error is ApiException, "replicas=$replicas 는 거부되어야 합니다")
        }
        assertEquals(null, client.scaledTo)
    }

    @Test
    fun `상태 조회가 실패해도 오류를 던지지 않는다`() {
        // 큐 모니터링 화면 전체가 실패로 무너지지 않게 한다.
        val service = service(readFailing(ScaleAccessFailure.UNKNOWN))

        val status = service.status(EXECUTOR)

        assertEquals(ExecutorScaleState.UNREADABLE, status.state)
    }

    @Test
    fun `실패 이유를 구분해 알린다`() {
        // **셋의 대응이 다르다** (#237). 권한 문제와 대상 없음이 같은 문구면 어디를 볼지 모른다.
        ScaleAccessFailure.entries.forEach { failure ->
            val status = service(readFailing(failure)).status(EXECUTOR)

            assertEquals(ExecutorScaleState.UNREADABLE, status.state)
            assertEquals(failure.message, status.reason)
        }
    }

    @Test
    fun `클러스터 밖과 읽기 실패는 다른 상태다`() {
        // 앞은 설정이고 뒤는 고장이다. 같은 값으로 내려가면 화면이 다르게 말할 수 없다.
        val outside = service(StubScaleClient(available = false)).status(EXECUTOR)
        val broken = service(readFailing(ScaleAccessFailure.FORBIDDEN)).status(EXECUTOR)

        assertEquals(ExecutorScaleState.OUTSIDE_CLUSTER, outside.state)
        assertEquals(ExecutorScaleState.UNREADABLE, broken.state)
        // 읽지 못해도 조정 수단까지 뺏지는 않는다 — 권한이 scale 에만 있을 수 있다.
        assertFalse(outside.controllable)
        assertTrue(broken.controllable)
    }

    @Test
    fun `읽지 못해도 조정은 시도한다`() {
        val client = readFailing(ScaleAccessFailure.FORBIDDEN)
        val service = service(client)

        service.scale(0, EXECUTOR, 5)

        assertEquals(5, client.scaledTo)
    }

    @Test
    fun `보고 있는 네임스페이스를 함께 알린다`() {
        // "그 배포가 없다" 는 말은 어느 네임스페이스에서 없다는 것인지 알아야 고칠 수 있다.
        val status = service(StubScaleClient(namespace = "codekr-exec")).status(EXECUTOR)

        assertEquals("codekr-exec", status.namespace)
    }

    @Test
    fun `대상마다 자기 네임스페이스를 본다`() {
        // 실행기는 Pod Security 가 느슨한 곳에 따로 놓일 수 있다 (#390).
        val client = StubScaleClient()
        service(client).status(EXECUTOR)

        assertEquals("codekr-exec", client.readNamespace)
    }

    @Test
    fun `허용 목록에 없는 이름은 조정되지 않는다`() {
        /*
          **경로에 이름을 받는다는 것은 아무 배포나 만질 문을 여는 것이다** (#390).
          설정에 적힌 것만 조정된다 — 일반화의 편의와 안전을 둘 다 가지는 방법이다.
        */
        val service = service(StubScaleClient())

        listOf("codekr-api", "kube-dns", "").forEach { name ->
            val error = runCatching { service.scale(0, name, 3) }.exceptionOrNull()
            assertTrue(
                error is ApiException && error.errorCode == ErrorCode.SCALING_UNAVAILABLE,
                "$name 은 조정되면 안 됩니다",
            )
        }
    }

    @Test
    fun `채점기는 워커 수를 조정한다`() {
        val workers = StubWorkers()
        val service = ExecutorScaleService(
            StubScaleClient(), properties, workers, org.mockito.Mockito.mock(AdminAuditService::class.java),
        )

        service.setWorkers(0, JUDGE, 12)

        // 채점기가 읽는 자리는 차선별이다 — 대회 채점기와 섞이면 안 된다.
        assertEquals(12, workers.written["general"])
    }

    @Test
    fun `실행기는 워커 수를 조정하지 않는다`() {
        // 파드 수와 워커 수는 듣는 곳이 다르다. 실행기에는 차선이 없다.
        val error = runCatching { service(StubScaleClient()).setWorkers(0, EXECUTOR, 4) }.exceptionOrNull()

        assertTrue(error is ApiException && error.errorCode == ErrorCode.VALIDATION_ERROR)
    }

    @Test
    fun `워커 수 0 은 거부한다`() {
        // 0 이면 그 차선의 채점이 멈추는데, 화면에서 그것은 "적체" 로 보인다 —
        // 원인이 조정이라는 것을 아무도 모른다.
        val service = service(StubScaleClient())

        listOf(0, -1, 65).forEach { workers ->
            assertTrue(
                runCatching { service.setWorkers(0, JUDGE, workers) }.exceptionOrNull() is ApiException,
                "workers=$workers 는 거부되어야 합니다",
            )
        }
    }

    @Test
    fun `정한 적이 없으면 워커 수는 null 이다`() {
        // 0 으로 내려보내면 화면이 "워커가 없다" 로 읽는다. 그때 채점기는 기동값을 쓴다.
        assertEquals(null, service(StubScaleClient()).status(JUDGE).workers)
    }

    @Test
    fun `조정할 수 있는 것을 전부 알려 준다`() {
        // 화면이 무엇이 있는지 서버에게 묻는다 — 대상이 늘어도 화면을 안 고친다.
        val keys = service(StubScaleClient()).statuses().map { it.key }

        assertEquals(listOf("executor", "judge"), keys)
    }

    private fun readFailing(failure: ScaleAccessFailure) =
        StubScaleClient(readError = ScaleAccessException(failure, "시험용"))
}
