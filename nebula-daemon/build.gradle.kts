@file:Suppress("VulnerableLibrariesLocal")

import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val targetJava = 25

plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":nebula-sdk"))
    implementation("me.devnatan:docker-kotlin:0.14.4")
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("io.ktor:ktor-server-cio:3.3.3")
    implementation("io.ktor:ktor-server-core:3.3.3")
    implementation("io.ktor:ktor-server-websockets:3.3.3")
}

application {
    mainClass = "nebula.MainKt"
    applicationDefaultJvmArgs = listOf(
        "--sun-misc-unsafe-memory-access=allow",
        "--enable-native-access=ALL-UNNAMED",
    )
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(targetJava))
    }
}

kotlin {
    jvmToolchain(targetJava)
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(targetJava.toString()))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(targetJava)
}

tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(targetJava))
        }
    )
}