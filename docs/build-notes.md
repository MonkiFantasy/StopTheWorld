# Build Notes

## Local environment caveat

This workspace currently runs on `aarch64`, while the Android SDK packages downloaded by `sdkmanager` provide `aapt2` binaries for `x86-64` Linux. Because Android resource processing depends on `aapt2`, local Android builds may fail here with an error similar to:

```text
AAPT2 ... Syntax error: "(" unexpected
```

The project is still configured as a standard Android Gradle project and should build on normal `x86-64` Linux CI runners, such as `ubuntu-latest` in GitHub Actions.

## Expected verification command

```bash
./gradlew testDebugUnitTest assembleDebug --stacktrace --no-daemon
```

## Required local setup

If building outside Android Studio, create a local `local.properties` file:

```properties
sdk.dir=/path/to/Android/Sdk
```

`local.properties` is intentionally ignored by Git.
