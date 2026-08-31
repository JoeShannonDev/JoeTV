import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")

    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use {
            load(it)
        }
    }
}

val googleClientId =
    localProperties.getProperty(
        "JOETV_GOOGLE_CLIENT_ID",
        ""
    )

val googleClientSecret =
    localProperties.getProperty(
        "JOETV_GOOGLE_CLIENT_SECRET",
        ""
    )

// Optional: a refresh token obtained once on a desktop machine via
// tools/get_google_refresh_token.py.
//
// The Pi has no browser and no signed-in Google account, so running the
// interactive OAuth flow on-device is impractical. Baking the refresh token in
// lets JoeTV mint access tokens on its own, survives a reinstall or a data
// wipe, and means Calendar just works at first boot.
val googleRefreshToken =
    localProperties.getProperty(
        "JOETV_GOOGLE_REFRESH_TOKEN",
        ""
    )

// Service account credentials -- how JoeTV actually reads the calendar.
//
// The calendar is *shared with* this service account, so there is no consent
// screen, no publishing status, and no refresh token to expire. Generate these
// three values from the downloaded service account JSON with
// tools/prepare_service_account.py.
val googleServiceAccountEmail =
    localProperties.getProperty(
        "JOETV_GOOGLE_SERVICE_ACCOUNT_EMAIL",
        ""
    )

val googleServiceAccountKey =
    localProperties.getProperty(
        "JOETV_GOOGLE_SERVICE_ACCOUNT_KEY",
        ""
    )

// Normally the calendar owner's Gmail address.
val googleCalendarId =
    localProperties.getProperty(
        "JOETV_GOOGLE_CALENDAR_ID",
        ""
    )

android {
    namespace = "com.joeshannon.joetv"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.joeshannon.joetv"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "GOOGLE_CLIENT_ID",
            "\"$googleClientId\""
        )

        buildConfigField(
            "String",
            "GOOGLE_CLIENT_SECRET",
            "\"$googleClientSecret\""
        )

        buildConfigField(
            "String",
            "GOOGLE_REFRESH_TOKEN",
            "\"$googleRefreshToken\""
        )

        buildConfigField(
            "String",
            "GOOGLE_SERVICE_ACCOUNT_EMAIL",
            "\"$googleServiceAccountEmail\""
        )

        buildConfigField(
            "String",
            "GOOGLE_SERVICE_ACCOUNT_KEY",
            "\"$googleServiceAccountKey\""
        )

        buildConfigField(
            "String",
            "GOOGLE_CALENDAR_ID",
            "\"$googleCalendarId\""
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}