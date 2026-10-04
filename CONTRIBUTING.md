# Contributing to HomePanel MR

Bug reports, feature proposals, documentation, translations, and pull requests are welcome. You may write issues and PRs in English or Vietnamese.

## Report a bug or propose a feature

Search [existing issues](https://github.com/hohiep102/homepanel-mr/issues) first, then choose the relevant [issue form](https://github.com/hohiep102/homepanel-mr/issues/new/choose). Include the app version, Community/Store edition, headset model and OS, Home Assistant version, reproduction steps, and expected/actual behavior. State whether your evidence comes from a real headset, emulator, or fixture.

Remove HA access/refresh tokens, passwords, private server addresses, device IDs, and room imagery from logs and screenshots. For vulnerabilities or exposed credentials, use [private security reporting](https://github.com/hohiep102/homepanel-mr/security/advisories/new), not a public issue.

## Open a pull request

1. Fork this repository and create a branch from `main`.
2. Build the Community edition; no publisher signing key or Meta purchase entitlement is needed.
3. Keep the change focused. For a large feature, discuss its scope in an issue first.
4. Add a regression test for behavior changes where it provides meaningful coverage. Keep app-provided strings aligned in `values/strings.xml` and `values-vi/strings.xml`; do not translate the user's own HA device names.
5. Run the applicable checks below and describe the result in the PR template. State any headset behavior you have not tested.
6. Open the PR against `main` and link the relevant issue.

```sh
./scripts/build.sh :app:testCommunityReleaseUnitTest :app:testStoreReleaseUnitTest :app:lintCommunityRelease :app:lintStoreRelease :app:assembleCommunityDebug :app:assembleStoreRelease
```

The Store release is unsigned without private signing configuration. CI uses no publisher signing keys, HA credentials, or Store secrets. Instrumentation tests are described in [Testing](docs/TESTING.md); run them only on a disposable emulator or a dedicated test device, because fixtures can reset app data.

## Design and implementation

- Shared app logic lives in `app/src/main`. Edition-specific entitlement gates live in `app/src/community` and `app/src/store`.
- Keep bare-hand interaction as the main Quest path. Controllers can provide optional shortcuts.
- Looking at an item or choosing a placement must not issue a Home Assistant service call. Commands must follow an explicit user action.
- Preserve encrypted credentials, bindings, and per-entity placement behavior across updates. Do not log credentials or silently bypass TLS verification.
- Community and Store should retain the same HA/MR feature set. Do not add a debug entitlement bypass to the Store gate.
- If dependencies change, regenerate both sets of third-party notices as described in [Distributions](docs/DISTRIBUTIONS.md).

Contributions are accepted under the repository's Apache-2.0 license, except third-party material that retains its stated license. Submit only code or assets you have the right to contribute, and preserve attribution. A separate contributor license agreement is not required.
