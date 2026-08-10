package codekr.api.scaling

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.properties.ExecutorScalingProperties
import codekr.api.scaling.service.ExecutorScaleClient
import codekr.api.scaling.service.ExecutorScaleService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class StubScaleClient(
    override val available: Boolean = true,
    override val unavailableReason: String? = null,
    private var desired: Int = 2,
    private var ready: Int = 2,
    private val failOnRead: Boolean = false,
) : ExecutorScaleClient {
    var scaledTo: Int? = null

    override fun read(deployment: String): Pair<Int, Int> {
        if (failOnRead) throw IllegalStateException("API 호출 실패")
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

        assertFalse(status.available)
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

        assertTrue(status.available)
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
    fun `상태 조회가 실패해도 오류를 던지지 않고 사용 불가로 알린다`() {
        // 큐 모니터링 화면 전체가 실패로 무너지지 않게 한다.
        val service = ExecutorScaleService(StubScaleClient(failOnRead = true), properties)

        val status = service.status()

        assertFalse(status.available)
        assertEquals("실행기 배포 상태를 읽지 못했습니다.", status.reason)
    }
}
