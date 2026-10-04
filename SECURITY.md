# Security

Report vulnerabilities privately through [GitHub private security reporting](https://github.com/hohiep102/homepanel-mr/security/advisories/new). Include the affected version/edition, reproduction steps using synthetic data, and the likely impact. Do not publish active credentials or details that expose someone else's Home Assistant server.

Security fixes are intended for the latest source on `main`. Older builds are not a separately maintained security branch. This community project does not promise a fixed response time.

## Scope

HomePanel stores HA credentials encrypted with Android Keystore, supports user-selected HTTP LAN servers, and validates HTTPS certificates and hostnames. An HTTP connection is not encrypted. Scene/anchor data is used locally for MR placement. Issues involving credential leakage, unintended HA commands, unsafe URL handling, or data exposure are relevant.

Publisher signing keys, account exports, and real HA configuration do not belong in this repository. The Meta App ID in the Store configuration is a public application identifier, not a credential.

`app/src/androidTest/assets/tls/localhost-test-only.p12` is intentionally public test material. It has the public password `fixture-only`, a self-signed localhost certificate, and no production role. Only instrumentation fixture clients explicitly trust it; production clients must reject it. Never reuse this fixture key in a deployed service.
