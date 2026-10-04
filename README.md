# HomePanel MR

[Tiếng Việt](README.vi.md) · [Report a bug](https://github.com/hohiep102/homepanel-mr/issues/new/choose) · [Contribute](CONTRIBUTING.md)

A mixed reality Home Assistant client for Meta Quest 3 and 3S. Browse rooms and devices, place controls beside objects in your room, and use hands to interact in passthrough. Controllers provide optional shortcuts.

![HomePanel Community running with demo devices](docs/images/community-demo.png)

*Android dashboard with demo data. This image is not evidence of headset tracking or real device control.*

## What it does

- Discovers Home Assistant on your LAN and supports browser sign-in; access-token entry is an optional advanced path.
- Organizes HA entities by room/device, with an Everyday view and an All devices view.
- Controls supported lights, switches, climate devices, fans, and covers; capabilities follow your HA integration.
- Places controls relative to room anchors and saves positions per entity/channel.
- Offers English and Vietnamese interfaces and an offline demo that needs no HA account.

MR runs while HomePanel is open in its immersive activity. It is not a persistent overlay over other applications. Objects are placed manually; the app does not recognize or track moving objects with AI. Hardware-specific behavior needs testing on your headset.

## One repository, two distributions

| | Community | Store |
|---|---|---|
| Distribution | Free sideload / build from source | Meta Horizon Store |
| HA and MR features | Shared | Shared |
| Meta purchase entitlement | Not required | Required |
| Release package | `vn.homepanel.mr.community` | `vn.homepanel.mr.store` |
| Meta Platform entitlement SDK | Excluded | Included |

Both editions use Meta Spatial SDK for MR. Debug builds have separate package IDs, and all variants have distinct browser return URIs. Editions keep separate data; moving from the old beta or another edition requires signing in and placing controls again. See [Distributions](docs/DISTRIBUTIONS.md).

The source version is **1.0.1**. The initial Store submission is under review as of October 4, 2026. A source build or APK does not establish Store availability. The latest 1.0.1 split passed local automated checks; a fresh MR acceptance run on the signed editions is still pending.

## Build Community

Install **JDK 17**, Android SDK Platform **36**, and Build Tools **36.0.0**. Set `JAVA_HOME` and `ANDROID_HOME`, or configure `sdk.dir` in a local, untracked `local.properties` file.

```sh
git clone https://github.com/hohiep102/homepanel-mr.git
cd homepanel-mr
./scripts/build.sh
```

This runs Community debug unit tests and builds `app/build/outputs/apk/community/debug/app-community-debug.apk`. No publisher signing key, Meta App ID setup, or purchase entitlement is needed for Community. On Windows, use Android Studio or `gradlew.bat :app:testCommunityDebugUnitTest :app:assembleCommunityDebug`.

Enable Developer Mode and USB debugging on a Quest 3/3S, connect it, and accept USB debugging inside the headset:

```sh
./scripts/install-quest.sh community debug
```

The script requires exactly one connected Quest 3/3S. It does not install on other Android devices. Open **HomePanel MR Community Debug** and choose **Explore demo** or **Connection**. Community debug, Community release, Store, and the old beta use separate package identities.

To test only the Android dashboard on an ARM64 emulator, use `adb -s <emulator-serial> install -r <apk>`. The emulator does not provide Quest passthrough, room anchors, or hand tracking.

## Connect Home Assistant

1. Put the headset and HA server on a reachable network. In Connection, select a discovered server or enter its URL.
2. Choose **Sign in to Home Assistant**. Authentication happens in the server's browser page.
3. After approval, use the return link to open the same HomePanel edition.
4. Select a device/function, enter the room, and place its control beside an object.

Your server and integrations determine available commands. HA credentials are encrypted locally with Android Keystore and excluded from backup. HTTPS certificate and hostname validation remain enabled. User-selected HTTP LAN connections are supported and are unencrypted. Credentials and room bindings are not shared between editions.

## Tests and contributions

Issues and PRs in English or Vietnamese are welcome. Use the [issue forms](https://github.com/hohiep102/homepanel-mr/issues/new/choose), or fork the repository and open a PR against `main`. See [CONTRIBUTING.md](CONTRIBUTING.md).

CI builds the Community debug and Store unsigned release variants, runs unit tests for both editions, checks lint, and compiles Community instrumentation tests. It uses no publisher signing secrets or real HA credentials.

Local 1.0.1 evidence: 85 unit tests per release edition, 24 Community debug Android tests, 2 Community release Android tests, 4 Store release Android tests, and 1 Store debug entitlement test passed. These are automated/emulator results. See [Testing](docs/TESTING.md) and [Headset checklist](docs/HAND_TESTING.md) for scope and limitations.

## License and dependencies

Original HomePanel code is licensed under [Apache-2.0](LICENSE). Dependency licenses and Meta SDK terms remain separate; this repository does not relicense those SDKs. See [NOTICE](NOTICE) and the in-app **About → Third-party licenses** screen.

HomePanel MR is an independent project. It is not affiliated with or endorsed by the Open Home Foundation or Meta, and it does not incorporate Home Assistant or Immersive Home application source.

[Website](https://homepanel-mr.pages.dev/) · [Privacy](https://homepanel-mr.pages.dev/privacy/) · [Support](https://homepanel-mr.pages.dev/support/) · [Security reporting](SECURITY.md)
