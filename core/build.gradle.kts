plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

// :core is a plain JVM library (no Android dependency) so every algorithm can be
// unit-tested against the real JDK (javax.crypto, java.util) instead of android.jar stubs.
kotlin {
    jvmToolchain(17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "256m"
    testLogging {
        events("failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
    }
}
