# Real-headset acceptance

Run these checks on a Quest 3 or 3S with the exact APK you intend to distribute. Begin with Demo. Record app version/edition, headset model and OS, and whether hands or controllers were used. Remove personal room imagery and HA credentials before sharing evidence.

- [ ] Open the app and navigate rooms/devices with hands; controllers are optional.
- [ ] Scroll and select with each hand. Read instructions and switch English/Vietnamese without clipped controls.
- [ ] A single press changes demo brightness, temperature, or fan speed by one step.
- [ ] Enter MR, grant/deny room permission, and verify the dashboard remains usable after denial.
- [ ] Load/capture the room, aim at an object, place a control, and confirm the saved entity/channel is correct.
- [ ] Interacting with a UI panel does not accidentally place another marker.
- [ ] The countdown placement path works without a controller.
- [ ] Looking/selecting/placing alone does not send an HA command; explicit control buttons do.
- [ ] Lost hand tracking, pinch holds, system gestures, or switching input modes do not trigger unintended actions.
- [ ] Move your head and close/reopen a control; panels remain reachable and do not repeatedly reopen after dismissal.
- [ ] Recenter and restart the app; matched room anchors restore correctly. Missing room/anchor data asks for placement rather than inventing a position.
- [ ] Complete HA browser sign-in on the headset and return to the correct edition with other editions installed.
- [ ] If testing a real appliance, deliberately choose a safe action and confirm the physical device response separately from HA's API acknowledgement.
- [ ] For Store, verify both an entitled account and an account without access. Check retry/exit behavior when the platform is unavailable.

Android UI screenshots and unit/fixture results are useful evidence for their own layer. They do not replace these headset and physical-device checks.
