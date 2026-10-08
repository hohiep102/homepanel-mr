# Testing HomePanel MR

Use Community for normal development. A local Android emulator can verify the dashboard, fixtures, storage, and protocol behavior. It cannot establish Quest hand tracking, passthrough, spatial anchors, or physical appliance behavior.

## Host checks

```sh
./scripts/build.sh :app:testCommunityReleaseUnitTest :app:testStoreReleaseUnitTest :app:lintCommunityRelease :app:lintStoreRelease :app:assembleCommunityDebug :app:assembleStoreRelease :app:assembleCommunityDebugAndroidTest
```

Store release is unsigned without publisher signing configuration. GitHub Actions runs these checks with read-only repository permissions and no signing or HA secrets.

## Headset test build

Run `scripts/install-for-test.sh` before handing the headset to a tester. It builds and installs the signed Store edition over the existing one (Home Assistant login and placements survive), removes every other HomePanel edition so an older build cannot be opened by mistake, and checks that the installed APK matches the build.

## Android instrumentation

Use an ARM64 API 36 emulator or a dedicated test device. The APK currently includes ARM64 native libraries. Instrumentation fixtures can reset app credentials and bindings, so do not run them on a headset connected to a real home. Always select a specific test serial.

```sh
./scripts/build.sh :app:assembleCommunityDebug :app:assembleCommunityDebugAndroidTest
adb -s <emulator-serial> install -r app/build/outputs/apk/community/debug/app-community-debug.apk
adb -s <emulator-serial> install -r app/build/outputs/apk/androidTest/community/debug/app-community-debug-androidTest.apk
adb -s <emulator-serial> shell am instrument -w -r vn.homepanel.mr.community.debug.test/androidx.test.runner.AndroidJUnitRunner
```

For signed release tests, build with `-PtestBuildType=release`. Use `CommunityReleaseTest` and `BrowserAuthUiTest` for the Community entry point/browser fixture, and `OwnershipReleaseTest` plus `TlsPolicyTest` for the Store negative-access and TLS checks. Common tests that create a test-only Compose activity require the debug test manifest and are intended for Community debug.

The TLS PKCS12 asset is an intentionally public, localhost-only test fixture. Its password is `fixture-only`. Test clients explicitly trust that fixture for positive/hostname checks; production clients reject its self-signed certificate. See [SECURITY.md](../SECURITY.md).

## Verified local snapshot — October 4, 2026

| Check | Result |
|---|---|
| Community release unit tests | 85 passed |
| Store release unit tests | 85 passed |
| Community debug unit tests | 85 passed |
| Community debug Android suite | 24 passed |
| Community signed release entry point and browser fixture | 2 passed |
| Store signed release negative access and TLS | 4 passed |
| Store debug negative entitlement | 1 passed |
| Release lint | 0 errors; 40 Community / 42 Store warnings, 1 hint each |
| Four variants installed together | Each return URI resolved to its own app |
| Release contents | Community excludes Platform entitlement SDK; Store includes it |

The browser fixture verifies the returned HTML links to the current distribution's URI. The Store negative tests verify that an unsupported emulator cannot access device controls; they do not prove a buyer's positive entitlement path on a real Quest. The 1.0.1 signed editions still need fresh MR/hand acceptance. Follow [HAND_TESTING.md](HAND_TESTING.md) and record the build, headset OS, and physical observations in your PR or issue.
