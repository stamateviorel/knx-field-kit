plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core-model"))
    implementation(libs.calimero.core)
    implementation(libs.calimero.device)
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.slf4j.simple)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
