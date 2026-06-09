# Build Notes

## Linux aarch64 / arm64 status

Google's official Linux Android SDK build-tools and the AAPT2 Maven artifacts currently ship Linux host binaries for `x86-64`, not Linux `aarch64`. That means a normal Android Gradle build on a Linux ARM64 machine can fail when AGP starts its bundled `aapt2`.

This workspace is `aarch64`, so the project uses this local workaround for verification:

1. Install Debian native Android build tools:

   ```bash
   apt-get update
   apt-get install -y aapt android-sdk-build-tools
   ```

2. Point Android Gradle Plugin to Debian's native ARM64 `aapt2` through a user-local Gradle property:

   ```properties
   android.aapt2FromMavenOverride=/usr/bin/aapt2
   ```

   Recommended location:

   ```text
   ~/.gradle/gradle.properties
   ```

   Do not put this absolute path in the repository-level `gradle.properties`, because x86_64 CI runners usually should use AGP's normal Maven-provided AAPT2.

3. Use `compileSdk = 34` for now. Debian's native ARM64 `aapt2` can link API 34 here, but failed against Google's API 35 `android.jar` with a resource-table parsing error.

## Verification command

```bash
./gradlew testDebugUnitTest assembleDebug --stacktrace --no-daemon
```

## CI recommendation

For stable release builds, prefer normal x86_64 Linux CI runners such as GitHub Actions `ubuntu-latest`. Linux ARM64 local builds are useful for development, but they depend on distro-packaged build tools that may lag behind Google's latest SDK.

## Required local setup

If building outside Android Studio, create a local `local.properties` file:

```properties
sdk.dir=/path/to/Android/Sdk
```

`local.properties` is intentionally ignored by Git.
