plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.xerial:sqlite-jdbc:3.45.2.0")
    implementation("org.json:json:20240303")
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.example.hospital_dashboard.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "HospitalDashboard"
            packageVersion = "0.8.0"
            description = "Hospital Dashboard Desktop"
            copyright = "© 2026 Hospital Dashboard"
            windows {
                menu = true
                shortcut = true
                perUserInstall = true
                dirChooser = true
            }
        }
    }
}
