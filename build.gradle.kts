plugins {
    kotlin("jvm") version "2.1.20"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

application {
    mainClass.set("com.projectpilot.RestApiServerKt")
}

group = "com.projectpilot"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    // Vert.x 5 dependencies
    implementation("io.vertx:vertx-core:5.0.7")
    implementation("io.vertx:vertx-web:5.0.7")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:5.0.7")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.2")
    // Logging dependencies
    implementation("ch.qos.logback:logback-classic:1.5.13")
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
    // MongoDB driver
    implementation("io.vertx:vertx-mongo-client:5.0.7")
    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.2")

    // JWT and authentication
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Password hashing
    implementation("at.favre.lib:bcrypt:0.10.2")

    // Email sending
    implementation("com.sun.mail:jakarta.mail:2.0.2")

    // Redis client
    implementation("io.vertx:vertx-redis-client:5.0.7")

    runtimeOnly("io.netty:netty-resolver-dns-native-macos:4.1.109.Final:osx-aarch_64")

}

tasks {
    shadowJar {
        archiveClassifier.set("all")
        manifest {
            attributes["Main-Class"] = "com.projectpilot.RestApiServerKt"
        }
    }
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("ProjectPilot")
    archiveClassifier.set("")
    archiveVersion.set("0.1.0")
}


tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}