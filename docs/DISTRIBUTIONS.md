# Community and Store distributions

HomePanel MR 1.0.2 (version code 15) uses one shared HA/UI/MR implementation with Gradle product flavors for distribution-specific access.

| | Community | Store |
|---|---|---|
| Release package | `vn.homepanel.mr.community` | `vn.homepanel.mr.store` |
| App label | HomePanel MR Community | HomePanel MR |
| Meta purchase entitlement | Not required | Required in every Store build |
| Meta Platform SDK | Excluded | 77.0.1 |
| Meta Spatial SDK | 0.14.0 | 0.14.0 |
| Browser return URI | `homepanelmr-community://open` | `homepanelmr-store://open` |

Debug variants add `.debug` to the package, `-debug` to the URI scheme, and `Debug` to the app label. All four variants can coexist. Both the manifest and `BuildConfig.AUTH_RETURN_SCHEME` come from the same variant configuration; the browser return page accepts these exact schemes.

## Build commands

Install JDK 17, Android Platform 36 and Build Tools 36.0.0, and set `JAVA_HOME`/`ANDROID_HOME` (or local `sdk.dir`).

```sh
# Community debug, including unit tests; no publisher credentials
./scripts/build.sh

# Both editions' unit tests and lint
./scripts/build.sh :app:testCommunityReleaseUnitTest :app:testStoreReleaseUnitTest :app:lintCommunityRelease :app:lintStoreRelease

# Unsigned releases for local inspection or signing with your own keys
./scripts/build.sh :app:assembleCommunityRelease :app:assembleStoreRelease
```

APKs are under `app/build/outputs/apk/<edition>/<buildType>/`. An unsigned release has the `-unsigned.apk` suffix. Use `gradlew.bat` with the same Gradle tasks on Windows.

Community development requires no Meta App ID registration or Store entitlement. Store builds target the published application's Meta App ID and still require a valid Meta entitlement; changing `DEBUG` does not bypass this gate. Use Community for normal development.

## Signed builds

Production signing keys are not in the repository. A maintainer with the appropriate local key configuration can run:

```sh
python3 scripts/build-release.py --edition community
python3 scripts/build-release.py --edition store
python3 scripts/build-release.py --edition all
```

The script reads ignored `.private-signing/community-signing.json` or `.private-signing/signing.json`, passes passwords through environment variables, and runs assemble, lint, and unit tests. Explicit Gradle tasks after `--edition` replace the defaults. Never submit signing configurations in an issue or PR.

For a new independent distribution only, `scripts/create-release-key.py --edition community` creates your own key and a private local backup. It refuses to overwrite an existing key. Keep the key for future updates; a new key cannot update an already installed app signed with a different key. Publishing an independent fork also requires its own package/branding decisions.

## Data and upgrades

The old beta package `vn.homepanel.mr` is separate. Community and Store do not automatically copy credentials, Android Keystore material, or room bindings from each other or the beta. Keep the existing app installed until you have configured the new edition.

Store 1.0.2 retains the package, signing identity, and return URI of Store 1.0.0. Separating the flavors did not replace the initial Store submission. The source repository contains both implementations; only Community is intended for unrestricted sideload use.

## Dependency notices

```sh
./scripts/build.sh :app:releaseLicenseInventory
python3 scripts/generate-notices.py --edition community
python3 scripts/generate-notices.py --edition store
```

The inventory is generated from each resolved release classpath. Notices live under `app/src/<edition>/assets/legal/`; machine-readable reports are generated under `app/build/reports/`. Regenerate notices after dependency changes and preserve upstream text. The current lists contain 68 Community and 69 Store dependencies; the extra Store dependency is Meta Platform SDK.

Apache-2.0 covers original HomePanel code. Third-party SDKs and notices retain their own terms. See [NOTICE](../NOTICE).
