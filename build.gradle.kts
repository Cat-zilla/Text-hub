// Root build file. Only plugin declarations live here (applied per module).
plugins {
    id("com.android.application") version "8.1.4" apply false
    id("com.android.library") version "8.1.4" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.22" apply false
}

tasks.register<Delete>("cleanRoot") {
    delete(rootProject.layout.buildDirectory)
}
