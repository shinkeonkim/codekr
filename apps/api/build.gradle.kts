plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    kotlin("plugin.jpa") version "2.4.10"
    kotlin("kapt") version "2.4.10"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
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

/*
    커버리지 (#642).

    **Kover 가 아니라 JaCoCo 다.** Kotlin 쪽 공식은 Kover 이고 그것을 먼저 붙여 봤는데,
    이 프로젝트의 `integrationTest` 소스셋에서 설정 단계부터 죽는다.

        Could not determine the dependencies of task ':koverGenerateArtifactJvm'.
        > Could not get unknown property 'compileKotlinTask' for compilation
          'integrationTest' (target  (jvm))

    그 소스셋을 빼면 Kover 는 돌지만 **그러면 잴 것이 거의 없다** — 시험 파일 114개 중
    95개가 통합 시험이고, 서비스·컨트롤러를 실제로 지나가는 것은 그쪽이다.
    도구를 지키려고 재려던 것을 버리는 셈이라 JaCoCo 로 간다.

    **둘을 합쳐서 본다.** 나누면 통합 시험이 덮은 코드가 단위 쪽 리포트에서 구멍으로
    보이고, 그 숫자를 보고 있으면 이미 시험된 것에 시험을 또 쓰게 된다.
*/
jacoco {
    toolVersion = "0.8.13"
}

tasks.withType<Test>().configureEach {
    // 각 시험 태스크가 자기 실행 기록을 따로 남긴다. 리포트가 그 둘을 모은다.
    extensions.configure<JacocoTaskExtension> {
        setDestinationFile(layout.buildDirectory.file("jacoco/${name}.exec").get().asFile)
    }
}

val coverageReport by tasks.registering(JacocoReport::class) {
    description = "단위 시험과 통합 시험을 합친 커버리지 리포트"
    group = "verification"

    // **시험을 여기서 부르지 않는다.** 부르게 하면 리포트를 보려고 Testcontainers 를
    // 반드시 띄워야 하고, 통합 시험 없이 단위 쪽만 보고 싶을 때 길이 없어진다.
    // 대신 `.exec` 가 있는 것만 모은다 — 무엇을 돌렸는지가 곧 무엇이 담기는지다.
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") })
    // 같은 실행에서 시험과 함께 부르면 Gradle 이 순서를 모른다 — 기록을 읽는 쪽이
    // 뒤라고 못박는다. 시험을 **부르지는** 않는다 (위 참조).
    mustRunAfter(tasks.withType<Test>())
    sourceDirectories.setFrom(sourceSets["main"].allSource.srcDirs)
    classDirectories.setFrom(
        files(sourceSets["main"].output.classesDirs).asFileTree.matching {
            // Q 클래스는 kapt 가 만든 것이라 사람이 시험할 대상이 아니다 (#642).
            // 남겨 두면 통째로 안 덮인 것으로 잡혀 전체 숫자를 끌어내린다.
            exclude("**/Q*.class")
        },
    )

    reports {
        html.required = true
        xml.required = true
        csv.required = false
    }

    /*
        **기록이 없으면 이 태스크는 조용히 SKIPPED 된다** — JaCoCo 가 그렇게 만들어져
        있고, `doFirst` 도 `doLast` 도 그때는 불리지 않는다. 그래서 "리포트가 나왔는가"
        는 여기서 못 지킨다. 확인은 `scripts/coverage.sh` 가 한다 —
        리포트 파일이 실제로 생겼는지 보고, 없으면 죽는다.
    */
}
