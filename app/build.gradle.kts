plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------------------
// Release signing — the owner's custodied key only, never the debug key
// (the family Sphinx pattern; secret names match the release-environment
// secrets sealed into this repo, identical to the Command App repo's).
//
// Signing material comes ONLY from the environment; nothing secret is
// stored in this repo:
//   SENTINEL_APP_KEYSTORE           path to the owner's PKCS12 keystore file
//   SENTINEL_APP_KEYSTORE_PASSWORD  keystore password
//   SENTINEL_APP_KEY_ALIAS          key alias inside the keystore
//   SENTINEL_APP_KEY_PASSWORD       key password
//
// All four set  -> release builds sign with the owner's key.
// Some set      -> configuration fails: a half-set signing setup is a
//                  mistake, not a mode. No fallback key exists.
// None set      -> release builds stay UNSIGNED (compile proof only);
//                  the signed* task below refuses to run.
// ---------------------------------------------------------------------------
val signingEnvNames = listOf(
    "SENTINEL_APP_KEYSTORE",
    "SENTINEL_APP_KEYSTORE_PASSWORD",
    "SENTINEL_APP_KEY_ALIAS",
    "SENTINEL_APP_KEY_PASSWORD",
)
val signingEnv = signingEnvNames.associateWith { System.getenv(it) }
val presentSigningEnv = signingEnv.filterValues { !it.isNullOrBlank() }
val releaseSigningReady = presentSigningEnv.size == signingEnvNames.size

if (presentSigningEnv.isNotEmpty() && !releaseSigningReady) {
    throw GradleException(
        "Sentinel Academy App release signing is only partly configured. Set all four of " +
            signingEnvNames.joinToString(", ") + " — or none of them. " +
            "A signed build needs the owner's custodied keystore; there is no fallback key."
    )
}

android {
    namespace = "net.sentinelsafeguard.academyapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.sentinelsafeguard.academyapp"
        minSdk = 26
        targetSdk = 35
        // 1.0.0 (2026-11-10, owner order): the FOUNDATION — theme,
        // motion, icon package, start screen, lock scaffolding.
        // 1.1.0 (2026-11-10, phase 2): the FULL app against the
        // LIVE Academy App API (contract BUILD RECORD 2026-11-10).
        // Signup/login/logout on the sessions-row token model
        // (SecureStore, 7-day fixed TTL, clean re-login path;
        // armed pending-confirmation shape handled); the PIN lock
        // wired onto the stored session; catalog + enroll + promo
        // entitlement; the road in the server's own order with
        // server-computed states; lessons rendered natively from
        // the six-type block vocabulary with /api/app/check
        // server-side grading (payloads carry no answer/explain —
        // there is no field here for one to land in); quizzes and
        // finals on the paper/score protocol with the honest
        // blurs count and the stable per-install sa-bid device id;
        // certificate issue (morph/tick — trust-adjacent) with the
        // ruled confetti celebration on the certificates screen
        // only; streaks card ("a guide, not a requirement");
        // verify via /api/app/verify ONLY (never the employer
        // API), malformed refused client-side and presented
        // identically to never-issued. access:"free" is display
        // data only — NO commerce surface exists in this build;
        // the paywall is a later phase by the owner's sequencing.
        versionCode = 4
        versionName = "1.1.2"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(signingEnv.getValue("SENTINEL_APP_KEYSTORE")!!)
                storePassword = signingEnv.getValue("SENTINEL_APP_KEYSTORE_PASSWORD")
                keyAlias = signingEnv.getValue("SENTINEL_APP_KEY_ALIAS")
                keyPassword = signingEnv.getValue("SENTINEL_APP_KEY_PASSWORD")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            // Obfuscation + shrinking for every release build. The
            // keep rules in proguard-rules.pro are deliberate and
            // enumerated; the mapping file R8 emits is archived by
            // CI as a build artifact and is NEVER committed (it is
            // the deobfuscation key for release crash reports).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/INDEX.LIST",
            )
        }
    }
}

val verifyReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails unless the owner's release signing is fully configured in the environment."
    doLast {
        if (!releaseSigningReady) {
            throw GradleException(
                "No signed Sentinel Academy App build can be made here: the signing environment is not set. " +
                    "Set SENTINEL_APP_KEYSTORE, SENTINEL_APP_KEYSTORE_PASSWORD, " +
                    "SENTINEL_APP_KEY_ALIAS and SENTINEL_APP_KEY_PASSWORD from the owner's " +
                    "custodied keystore secrets, then run the signed task again. Unsigned and " +
                    "debug-signed builds are compile proofs only — never releases."
            )
        }
    }
}

tasks.register("signedRelease") {
    group = "build"
    description = "Builds the signed release APK with the owner's keystore (refuses without it)."
    dependsOn(verifyReleaseSigning, "assembleRelease")
}

tasks.configureEach {
    if (name == "assembleRelease") {
        mustRunAfter(verifyReleaseSigning)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Lifecycle anchors at the family's pinned 2.8.7 (the Mail App's
    // pattern). The code genuinely uses both: viewModel() from
    // lifecycle-viewmodel-compose (AppRoot) and the lifecycle runtime
    // the Compose lifecycle owner stands on (AndroidViewModel /
    // viewModelScope in AcademyViewModel). Without these anchors the
    // graph settles on the raw transitive requests (lifecycle 2.6.x,
    // arch.core 2.1.0) — versions that are neither pinned in
    // gradle/verification-metadata.xml nor carried by the family
    // seed repo, which is exactly how the first Linode release build
    // failed at dependency resolution (2026-11-10).
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    constraints {
        // Pin the rest of the drift to the versions already pinned
        // in gradle/verification-metadata.xml (lifecycle 2.8.7
        // family, arch.core 2.2.0). Constraints only — no new
        // dependencies, no pin changes, no downgrades: every module
        // named here is already requested transitively at an older
        // version (savedstate -> arch.core 2.1.0; the 2.6.x lifecycle
        // requests from activity-compose / activity-ktx / emoji2 and
        // the 2.6.x family's own alignment constraints).
        implementation("androidx.lifecycle:lifecycle-common:2.8.7")
        implementation("androidx.lifecycle:lifecycle-common-java8:2.8.7")
        implementation("androidx.lifecycle:lifecycle-process:2.8.7")
        implementation("androidx.lifecycle:lifecycle-runtime:2.8.7")
        implementation("androidx.arch.core:core-common:2.2.0")
        implementation("androidx.arch.core:core-runtime:2.2.0")
    }

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // On-device security: Keystore-backed encrypted storage for the
    // app-lock scaffolding (phase 2 wires the lock itself).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
