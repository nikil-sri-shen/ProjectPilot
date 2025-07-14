plugins {
    kotlin("jvm") version "2.1.20"
}

group = "com.projectpilot"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("io.vertx:vertx-core:4.5.1")
    implementation("io.vertx:vertx-web:4.5.1")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:4.5.1")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.2")
    // Logging dependencies
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    // MongoDB driver
    implementation("io.vertx:vertx-mongo-client:4.5.1")
    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.1")

    // JWT and authentication
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Password hashing
    implementation("at.favre.lib:bcrypt:0.10.2")

    runtimeOnly("io.netty:netty-resolver-dns-native-macos:4.1.109.Final:osx-aarch_64")

}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(22)
}