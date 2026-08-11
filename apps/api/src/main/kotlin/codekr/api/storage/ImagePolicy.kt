package codekr.api.storage

/**
 * 용도별 이미지 규격 (#115).
 *
 * **용도가 규격을 정한다.** 아바타는 목록에서 수십 개가 함께 뜨므로 작아야 하고, 본문에
 * 붙는 이미지는 읽을 수 있어야 하므로 그보다 크다. 하나로 통일하면 아바타에 큰 규격이
 * 적용되어 낭비가 되거나, 첨부가 아바타 규격으로 뭉개진다.
 *
 * 상한(용량·픽셀 수)은 용도와 무관하게 [ImageLimits] 에서 한 번만 정한다.
 */
enum class ImagePolicy(
    /** 결과의 최대 변 길이(픽셀). */
    val maxEdge: Int,
    val format: String,
    val contentType: String,
    val extension: String,
    /** 정사각형으로 잘라낼지. 아니면 비율을 유지한 채 [maxEdge] 안에 맞춘다. */
    val squareCrop: Boolean,
    /**
     * JPEG 품질(0~1). PNG 는 무손실이라 쓰지 않는다.
     *
     * 0.8 은 사진에서 눈에 띄는 손실 없이 크기를 크게 줄이는 통상적인 값이다.
     */
    val quality: Float? = null,
) {
    /**
     * 프로필 아바타 (#116).
     *
     * PNG 인 이유: 원형으로 잘라 보여주므로 **투명도가 필요하다.** JPEG 은 알파가 없어
     * 투명한 배경이 검게 나온다. 256px 에서는 PNG 의 크기 불이익도 크지 않다.
     */
    AVATAR(maxEdge = 256, format = "png", contentType = "image/png", extension = "png", squareCrop = true),

    /**
     * 본문에 붙이는 이미지.
     *
     * JPEG 인 이유: 사진이 대부분이라 손실 압축이 크게 이득이고, **JDK 가 기본으로 쓸 수
     * 있다.** WebP 가 20~30% 더 줄지만 JDK 에 라이터가 없어 네이티브 라이브러리를 하나 더
     * 들여야 한다 — 배포 아키텍처마다 동작을 확인해야 하는 대가가 이득보다 크다.
     */
    ATTACHMENT(
        maxEdge = 1600,
        format = "jpg",
        contentType = "image/jpeg",
        extension = "jpg",
        squareCrop = false,
        quality = 0.8f,
    ),
}

/**
 * 용도와 무관한 상한.
 *
 * **여기서 막는 것은 저장 용량이 아니라 디코딩이다.** 다시 인코딩하므로 저장되는 크기는
 * 입력과 무관하다. 문제는 디코딩 자체가 공격이 될 수 있다는 점이다.
 */
object ImageLimits {

    /** 받아 줄 원본의 최대 바이트. */
    const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024

    /**
     * 디코딩을 허용할 최대 픽셀 수.
     *
     * **압축 폭탄을 막는다.** 파일 크기 상한만으로는 부족하다 — 한 가지 색으로 채운
     * 50000×50000 PNG 는 수백 KB 로 압축되지만, 디코딩하면 픽셀당 4바이트로 10GB 가 된다.
     * 헤더의 크기만 읽어 판단하므로 그 메모리를 쓰기 전에 막는다.
     *
     * 4천만 픽셀은 8000×5000 으로, 어떤 카메라 사진보다도 크다.
     */
    const val MAX_PIXELS = 40_000_000L
}
