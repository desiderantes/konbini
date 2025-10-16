import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlin.io.path.Path
import kotlin.io.path.inputStream

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.benmanes.versions)
    alias(libs.plugins.kotlinx.kover)
    `maven-publish`
}

group = "cc.ekblad"
version = "0.1.3"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        java {
            targetCompatibility = JavaVersion.VERSION_1_8
            withSourcesJar()
            withJavadocJar()
        }

        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled = true
                }
            }
        }
    }
    macosX64()
    linuxX64()
    mingwX64()

    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting
        val jvmTest by getting
        val jsMain by getting
        val jsTest by getting
    }
}

publishing {
    publications {
        create<MavenPublication>("konbini") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components["kotlin"])
            pom {
                name.set(project.name)
                description.set("Lightweight parser combinator library")
                url.set("https://github.com/valderman/konbini")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://github.com/valderman/konbini/blob/main/LICENSE")
                    }
                }
                developers {
                    developer {
                        id.set("valderman")
                        name.set("Anton Ekblad")
                        email.set("anton@ekblad.cc")
                    }
                }
            }
        }
    }
}

data class DependencyVersion(val module: String, val version: String)

val excludedVersions: Set<Pair<String, String>> =
    setOf(
        // Forcing this build-time dependency is a bit messy
        "intellij-coverage-agent" to "1.0.681",
        "intellij-coverage-reporter" to "1.0.681",
        "intellij-coverage-agent" to "1.0.682",
        "intellij-coverage-reporter" to "1.0.682",
    )

ktlint {
    version.set(libs.versions.ktlint)
}

fun notProductionVersion(version: String): Boolean = listOf(
    "beta",
    "rc",
    "m1",
    "m2",
    "alpha",
    "snapshot",
    "beta1",
    "beta2",
    "beta3",
    "rc1",
    "rc2",
    "rc3",
    "rc-1",
    "rc-2",
    "rc-3",
).any { version.lowercase().endsWith(it) }

tasks {
    val allTests by getting

    dependencyUpdates {
        rejectVersionIf {
            (candidate.module to candidate.version) in excludedVersions || notProductionVersion(candidate.version)
        }
    }

    val dependencyUpdateSentinel =
        register<DependencyUpdateSentinel>("dependencyUpdateSentinel") {
            dependsOn(dependencyUpdates)
        }

    allTests.apply {
        finalizedBy(koverVerify)
    }

    kover {
        reports {
            verify {
                rule {
                    bound {
                        minValue = 80
                        coverageUnits = CoverageUnit.BRANCH
                        aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                    }

                    bound {
                        minValue = 80
                        coverageUnits = CoverageUnit.LINE
                        aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                    }
                }
            }
        }
    }

    build {
        finalizedBy(dokkaGenerate)
    }

    check {
        // dependsOn(test)
        dependsOn(ktlintCheck)
        dependsOn(dependencyUpdateSentinel)
    }
}

abstract class DependencyUpdateSentinel : DefaultTask() {
    @TaskAction
    fun check() {
        val updateIndicator = "The following dependencies have later milestone versions:"
        Path("build", "dependencyUpdates", "report.txt").inputStream().bufferedReader().use { reader ->
            if (reader.lines().anyMatch { it == updateIndicator }) {
                logger.warn("Dependency updates are available.")
                // throw GradleException("Dependency updates are available.")
            }
        }
    }
}
