plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    kotlin("plugin.jpa") version "2.4.10"
    kotlin("kapt") version "2.4.10"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "codekr"
version = "0.1.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

val querydslVersion = "5.1.0"
val testcontainersVersion = "2.0.5"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Spring Boot 4 의 기본 JSON 스택은 Jackson 3 (tools.jackson) 이다.
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Spring Boot 4 는 자동 구성이 기술별 모듈로 분리돼 있다 — flyway-core 만으로는 동작하지 않는다.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // 동적 검색 조건(키워드/카테고리/난이도/정렬)을 타입 안전하게 조립하기 위한 Querydsl
    implementation("com.querydsl:querydsl-jpa:$querydslVersion:jakarta")
    kapt("com.querydsl:querydsl-apt:$querydslVersion:jakarta")
    kapt("jakarta.annotation:jakarta.annotation-api")
    kapt("jakarta.persistence:jakarta.persistence-api")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    // jjwt 의 Jackson 직렬화기는 Jackson 2 를 요구하므로 해당 의존성을 함께 가져온다.
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // 오브젝트 스토리지 (#115). S3 호환 API 하나로 운영(S3)과 로컬(MinIO)을 함께 쓴다.
    // apache-client 대신 url-connection-client 를 쓰는 이유: 의존성이 훨씬 가볍고,
    // 아바타 크기의 요청에는 커넥션 풀링의 이득이 없다.
    implementation("software.amazon.awssdk:s3:2.31.78") {
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
        exclude(group = "software.amazon.awssdk", module = "apache-client")
    }
    implementation("software.amazon.awssdk:url-connection-client:2.31.78")

    // 메일 발송 (#233). **직접 MTA 를 운영하지 않는다** — 발송 서비스의 SMTP 엔드포인트에
    // 붙는다. 그러면 스팸 처리·바운스·평판 관리는 그 서비스가 지고, 우리는 벤더 SDK 를
    // 하나 더 들이지 않는다 (#106 이 외부 발송을 피했던 이유의 절반은 여기서 해소된다).
    implementation("org.springframework.boot:spring-boot-starter-mail")

    implementation("io.micrometer:micrometer-registry-prometheus")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // 통합 테스트 전용 (integrationTest 소스셋에서 사용)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainersVersion")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// 무거운 통합 테스트(Testcontainers)는 기본 `test` 태스크에서 분리한다 — CI 에서 별도 잡으로 실행.
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
        runtimeClasspath += output + compileClasspath
    }
}

// integrationTest 소스셋에는 애노테이션 프로세싱 대상이 없다 (Q 클래스는 main 에서 생성됨).
tasks.matching { it.name.startsWith("kapt") && it.name.contains("IntegrationTest") }
    .configureEach { enabled = false }

val integrationTest by tasks.registering(Test::class) {
    description = "Testcontainers 기반 통합 테스트"
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "app.jar"
}
