import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "workbench"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("com.jcraft:jsch:0.1.55")
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "workbench.MainKt"

        // Java 21 class files break bundled ProGuard 7.2.2 — keep packaging without it
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "remote-workbench"
            packageVersion = version.toString()
            description = "SSH shell + SFTP file tree workbench (POC)"
            copyright = "Copyright (c) 2026"
            vendor = "thdrmsqhd"

            windows {
                menuGroup = "remote-workbench"
                // per-install upgrade id (stable for this app)
                upgradeUuid = "A3F5C8D1-2E4B-4A6C-9D0E-1F2A3B4C5D6E"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
