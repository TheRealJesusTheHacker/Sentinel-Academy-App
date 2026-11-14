import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
    id("org.jetbrains.compose") version "1.7.3"
}

// ---------------------------------------------------------------------------
// Sentinel Academy Desktop 1.0.0 — Compose Multiplatform port of the
// Android app. This module compiles the SHARED sources directly from
// ../app/src/main/java (the pure data layer + every UI screen) and
// adds only the desktop platform seams (Platform.kt, DesktopStore.kt,
// Main.kt). The Android module is a separate build and is untouched
// by this file. See ../PROGRAM records / desktop/README.md.
// ---------------------------------------------------------------------------

val sharedUiAndData = listOf(
    "data/Models.kt",
    "data/AcademyLogic.kt",
    "data/CatalogCache.kt",
    "data/AppStore.kt",
    "data/AcademyApi.kt",
    "ui/AcademyViewModel.kt",
    "ui/AppRoot.kt",
    "ui/AuthScreens.kt",
    "ui/CertsSettings.kt",
    "ui/Components.kt",
    "ui/Confetti.kt",
    "ui/ExamScreen.kt",
    "ui/HomeScreens.kt",
    "ui/LessonScreen.kt",
    "ui/Motion.kt",
    "ui/MotionLogic.kt",
    "ui/theme/Theme.kt",
)

sourceSets {
    main {
        kotlin {
            srcDir("src/main/kotlin")
            srcDir("../app/src/main/java/net/sentinelsafeguard/academyapp")
            // Include patterns are relative to EACH srcDir root:
            // the shared list selects files under the academyapp
            // package dir; the net/** pattern selects this module's
            // own sources (and the generated R/DesktopStrings).
            include(sharedUiAndData + "net/sentinelsafeguard/academyapp/**/*.kt")
        }
    }
}

// --- Generated R + string tables, from the SAME strings.xml files ---
// The desktop R class carries the same key->id shape the shared UI
// sources compile against on Android, and DesktopStrings holds the
// EN/ES copy parsed from app/src/main/res/values{,-es}/strings.xml
// at build time — the desktop copy cannot drift from the phone's.
val generateDesktopStrings = tasks.register("generateDesktopStrings") {
    val enFile = file("../app/src/main/res/values/strings.xml")
    val esFile = file("../app/src/main/res/values-es/strings.xml")
    val outDir = layout.buildDirectory.dir("generated/desktopStrings")
    inputs.files(enFile, esFile)
    outputs.dir(outDir)
    doLast {
        fun parse(f: File): List<Pair<String, String>> {
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(f)
            val nodes = doc.getElementsByTagName("string")
            val out = mutableListOf<Pair<String, String>>()
            for (i in 0 until nodes.length) {
                val n = nodes.item(i)
                out += n.attributes.getNamedItem("name").nodeValue to n.textContent
            }
            return out
        }
        fun ktEscape(s: String): String = s
            .replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
            .replace("\n", "\\n").replace("\r", "").replace("\t", "\\t")
        val en = parse(enFile)
        val es = parse(esFile)
        require(en.map { it.first } == es.map { it.first }) {
            "EN/ES string key order/sets differ — parity broken"
        }
        val ids = en.mapIndexed { idx, (k, _) -> k to (idx + 1) }.toMap()
        val rOut = outDir.get().dir("net/sentinelsafeguard/academyapp").file("R.kt").asFile
        rOut.parentFile.mkdirs()
        rOut.writeText(buildString {
            appendLine("package net.sentinelsafeguard.academyapp")
            appendLine()
            appendLine("// GENERATED at build time from strings.xml — do not edit.")
            appendLine("object R {")
            appendLine("    object string {")
            for ((k, v) in ids) appendLine("        const val $k: Int = $v")
            appendLine("    }")
            appendLine("    object drawable {")
            appendLine("        const val academy_mark: Int = 10001")
            appendLine("        const val sentinel_logo: Int = 10002")
            appendLine("    }")
            appendLine("}")
        })
        fun table(name: String, pairs: List<Pair<String, String>>): String = buildString {
            appendLine("    val $name: Map<Int, String> = mapOf(")
            for ((k, v) in pairs) appendLine("        ${ids[k]} to \"${ktEscape(v)}\",")
            appendLine("    )")
        }
        val sOut = outDir.get().dir("net/sentinelsafeguard/academyapp/desktop").file("DesktopStrings.kt").asFile
        sOut.parentFile.mkdirs()
        sOut.writeText(buildString {
            appendLine("package net.sentinelsafeguard.academyapp.desktop")
            appendLine()
            appendLine("// GENERATED at build time from the Android app's strings.xml")
            appendLine("// (values + values-es) — the desktop copy IS the phone copy.")
            appendLine("object DesktopStrings {")
            append(table("EN", en))
            append(table("ES", es))
            appendLine("}")
        })
    }
}
sourceSets.main.get().kotlin.srcDir(generateDesktopStrings.map { it.outputs.files })

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.json:json:20240303")
    // JNA: DPAPI (Windows, via jna-platform Crypt32Util) and
    // libsecret / Secret Service (Linux) for the credential store.
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "net.sentinelsafeguard.academyapp.desktop.MainKt"

    buildTypes.release.proguard {
        configurationFiles.from("proguard-rules.pro")
    }
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Exe)
            packageName = "sentinel-academy"
            packageVersion = "1.0.0"
            description = "Sentinel Academy — courses, exams and certificates from Sentinel Safeguard"
            vendor = "Sentinel Safeguard LLC"
            linux {
                packageName = "sentinel-academy"
                appCategory = "Education"
                iconFile.set(project.file("../brand/sentinel-academy-icon-master.png"))
            }
            windows {
                packageName = "Sentinel Academy"
                iconFile.set(project.file("../brand/sentinel-academy-icon-master.png"))
                dirChooser = true
                menuGroup = "Sentinel Safeguard"
                upgradeUuid = "b7e2a3c1-5d4f-4a6b-9c8d-1e2f3a4b5c6d"
            }
        }
    }
}
