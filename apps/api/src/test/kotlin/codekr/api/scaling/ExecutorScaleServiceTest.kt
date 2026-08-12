package codekr.api.scaling

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.properties.ExecutorScalingProperties
import codekr.api.scaling.dto.ExecutorScaleState
import codekr.api.scaling.service.ExecutorScaleClient
import codekr.api.scaling.service.ExecutorScaleService
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

    override fun read(deployment: String): Pair<Int, Int> {
        readError?.let { throw it }
        return desired to ready
    }

    override fun scale(deployment: String, replicas: Int) {
        scaledTo = replicas
        desired = replicas
    }
}

class ExecutorScaleServiceTest {

    private val properties = ExecutorScalingProperties(deployment = "codekr-executor", minReplicas = 1, maxReplicas = 10)

    @Test
    fun `클러스터 밖에서는 사용 불가 상태를 돌려준다`() {
        val service = ExecutorScaleService(
            StubScaleClient(available = false, unavailableReason = "클러스터 밖"),
            properties,
        )

        val status = service.status()

        assertEquals(ExecutorScaleState.OUTSIDE_CLUSTER, status.state)
        assertEquals("클러스터 밖", status.reason)
    }

    @Test
    fun `클러스터 밖에서 조정을 시도하면 거부한다`() {
        val service = ExecutorScaleService(StubScaleClient(available = false), properties)

        val error = runCatching { service.scale(3) }.exceptionOrNull()

        assertTrue(error is ApiException && error.errorCode == ErrorCode.SCALING_UNAVAILABLE)
    }

    @Test
    fun `현재 replica 수를 읽는다`() {
        val service = ExecutorScaleService(StubScaleClient(desired = 4, ready = 3), properties)

        val status = service.status()

        assertEquals(ExecutorScaleState.OK, status.state)
        assertEquals(4, status.desiredReplicas)
        assertEquals(3, status.readyReplicas)
    }

    @Test
    fun `허용 범위 안이면 조정한다`() {
        val client = StubScaleClient()
        val service = ExecutorScaleService(client, properties)

        service.scale(5)

        assertEquals(5, client.scaledTo)
    }

    @Test
    fun `허용 범위를 벗어나면 거부한다`() {
        // 실수로 큰 수를 넣어 노드를 마비시키는 일을 막는다.
        val client = StubScaleClient()
        val service = ExecutorScaleService(client, properties)

        listOf(0, 11, 200).forEach { replicas ->
            val error = runCatching { service.scale(replicas) }.exceptionOrNull()
            assertTrue(error is ApiException, "replicas=$replicas 는 거부되어야 합니다")
        }
        assertEquals(null, client.scaledTo)
    }

    @Test
    fun `상태 조회가 실패해도 오류를 던지지 않는다`() {
        // 큐 모니터링 화면 전체가 실패로 무너지지 않게 한다.
        val service = ExecutorScaleService(readFailing(ScaleAccessFailure.UNKNOWN), properties)

        val status = service.status()

        assertEquals(ExecutorScaleState.UNREADABLE, status.state)
    }

    @Test
    fun `실패 이유를 구분해 알린다`() {
        // **셋의 대응이 다르다** (#237). 권한 문제와 대상 없음이 같은 문구면 어디를 볼지 모른다.
        ScaleAccessFailure.entries.forEach { failure ->
            val status = ExecutorScaleService(readFailing(failure), properties).status()

            assertEquals(ExecutorScaleState.UNREADABLE, status.state)
            assertEquals(failure.message, status.reason)
        }
    }

    @Test
    fun `클러스터 밖과 읽기 실패는 다른 상태다`() {
        // 앞은 설정이고 뒤는 고장이다. 같은 값으로 내려가면 화면이 다르게 말할 수 없다.
        val outside = ExecutorScaleService(StubScaleClient(available = false), properties).status()
        val broken = ExecutorScaleService(readFailing(ScaleAccessFailure.FORBIDDEN), properties).status()

        assertEquals(ExecutorScaleState.OUTSIDE_CLUSTER, outside.state)
        assertEquals(ExecutorScaleState.UNREADABLE, broken.state)
        // 읽지 못해도 조정 수단까지 뺏지는 않는다 — 권한이 scale 에만 있을 수 있다.
        assertFalse(outside.controllable)
        assertTrue(broken.controllable)
    }

    @Test
    fun `읽지 못해도 조정은 시도한다`() {
        val client = readFailing(ScaleAccessFailure.FORBIDDEN)
        val service = ExecutorScaleService(client, properties)

        service.scale(5)

        assertEquals(5, client.scaledTo)
    }

    @Test
    fun `보고 있는 네임스페이스를 함께 알린다`() {
        // "그 배포가 없다" 는 말은 어느 네임스페이스에서 없다는 것인지 알아야 고칠 수 있다.
        val status = ExecutorScaleService(StubScaleClient(namespace = "codekr-exec"), properties).status()

        assertEquals("codekr-exec", status.namespace)
    }

    private fun readFailing(failure: ScaleAccessFailure) =
        StubScaleClient(readError = ScaleAccessException(failure, "시험용"))
}
