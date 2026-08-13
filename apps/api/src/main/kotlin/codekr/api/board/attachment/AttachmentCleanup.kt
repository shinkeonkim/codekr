package codekr.api.board.attachment

import codekr.api.storage.ObjectStorage
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * 주인 없는 이미지를 치운다 (#389, #46).
 *
 * 올려 놓고 글을 안 쓰면 파일만 남는다. 이슈가 둘을 놓고 **"일정 시간 뒤 안 쓰인 것을
 * 치운다"** 쪽을 봤고, 그대로 갔다 — 다른 쪽(글 저장 때 본문을 파싱해 정리)은 정확하지만
 * **마크다운 규칙이 바뀔 때마다 함께 바뀌어야 하고, 틀리면 쓰고 있는 이미지를 지운다.**
 *
 * **유예를 길게 둔다.** 글을 쓰다 만 사람이 다음 날 이어 쓰는 일은 흔하다. 하루가 아니라
 * 이레를 두는 이유가 그것이다 — 여기서 급할 이유가 없다.
 */
@Component
class AttachmentCleanup(
    private val attachments: PostAttachmentRepository,
    private val storage: ObjectStorage,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 정리 배치와 같은 시각대(#46). 사용량이 가장 적고, 실패해도 다음 날 다시 한다. */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    @Transactional
    fun cleanup(): Int {
        if (!storage.available) return 0

        val orphans = attachments.findOrphans(Instant.now().minus(GRACE))
        if (orphans.isEmpty()) return 0

        orphans.forEach { attachment ->
            /*
                **파일을 먼저 지우고 행을 지운다.**

                반대로 하면 행이 사라진 뒤 파일 삭제가 실패했을 때 그 파일을 다시 찾을
                길이 없다 — 스토리지에는 나이도 주인도 없다. 이 순서면 최악의 경우
                다음 날 다시 시도한다.
            */
            runCatching { storage.delete(attachment.storageKey) }
                .onFailure { log.warn("첨부 파일 삭제 실패 key={}", attachment.storageKey, it) }
                .onSuccess { attachments.delete(attachment) }
        }

        log.info("주인 없는 첨부 이미지 {}개를 치웠습니다", orphans.size)
        return orphans.size
    }

    private companion object {
        /** 올린 뒤 이 기간이 지나도 아무 글에 안 쓰였으면 주인이 없는 것으로 본다. */
        val GRACE: Duration = Duration.ofDays(7)
    }
}
