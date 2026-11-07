# Google Play

Everything the Play Console asks for, ready to paste or upload.

| What | Where |
|---|---|
| App ID | `dev.yesil.callsoul` |
| Title, short and full description (tr-TR, en-US, ar, ru-RU) | `listing/<lang>/` |
| Release notes | `release-notes/<lang>.txt` |
| App icon 512x512 | `graphics/icon-512.png` |
| Feature graphic 1024x500 | `graphics/feature-graphic.png` (tr), `graphics/feature-graphic-en.png` (en) |
| Phone screenshots 1080x1920 | `graphics/screenshots/<lang>/` |
| Privacy policy (draft) | `privacy-policy.md` |

Scripts in `tools/` regenerate the icon (`icon.py`), check text lengths (`texts.py`) and build the
mockups (`mockups.py`) from raw captures in `graphics/raw/` (not in git).

Screenshots: eight per language for Turkish and English, captured on the Pixel 9 Pro emulator
with fictional people. Play shows the default-language set for Arabic and Russian until they get
their own. The English feature graphic is `graphics/feature-graphic-en.png`.

## Store settings

- Play Console: developer account Solid Electron, app `dev.yesil.callsoul`, default language tr-TR, free.
- Category: Communication. Tags (Console list): Arayan Kimliği, Araçlar, Gizlilik ve güvenlik, Haberleşme, Verimlilik.
- Contact: solidect38@gmail.com, https://yesil.dev.
- Privacy policy: https://callsoul.yesil.dev/privacy. Data deletion: https://callsoul.yesil.dev/delete-data.
  Both are built from `privacy-policy.md` and `data-deletion.md` by `tools/privacy.py` into the server.
- Content rating: Communication; users can report content; no location sharing, purchases or chat.
- Target audience: 18+ (keeps the app outside the Families policy).
- Ads: none. Advertising ID: not used.
- Don't name Samsung, Galaxy or One UI in the listing: they are Samsung's trademarks.

## Data safety form

Data collected (sent off the device):

| Data type | Collected | Shared | Optional | Why |
|---|---|---|---|---|
| Phone number of an unknown caller (see note) | Yes | No | Yes (setting) | App functionality: spam warning and caller ID |
| Spam answer, category, suggested caller name | Yes | No | Yes (only when the user answers) | App functionality |
| Suggested caller name (Personal info > Name) | Yes | No | Yes | App functionality |
| Unknown caller's number and suggested name (Contacts) | Yes | No | Yes | App functionality; Play's quick check asks for this type |
| Random install ID (Device or other IDs) | Yes | No | Yes | Fraud prevention: one vote per install |

Note: Google's form has no exact category for "a third party's phone number looked up for caller
ID". Caller-ID apps usually declare **Personal info > Phone numbers** (collected, not shared,
optional, App functionality). Do that; it is the cautious choice.

- Data is encrypted in transit: Yes (HTTPS; make sure the release server URL is `https://`).
- Users can request deletion: Yes (by e-mail, see the privacy policy).
- Not collected: contacts, call log, location, the user's own number, identifiers such as the
  advertising ID. They are read on the device only.

## Permissions to justify

- **Default phone app (Telecom role), call log, contacts, phone numbers**: core function. Play's
  SMS/Call Log policy allows these for the default dialer; pick "Default Phone handler" in the
  permissions declaration form.
- **Display over other apps**: the floating call pill; granted with the dialer role.
- **Full-screen intent**: incoming calls on the lock screen (allowed for calling apps).
- **Foreground service (phone call)**: the in-call service.

## Release

- The upload key is `~/keys/callsoul-upload.jks`; its path and password are in `~/.gradle/gradle.properties`
  (`bcs.uploadStore`, `bcs.uploadPassword`). Keep a backup of both outside this computer.
- Build with `./gradlew :app:bundleRelease`; the bundle is `android/app/build/outputs/bundle/release/app-release.aab`.
- Screens come from the emulator with fictional people, never from a real phone.
- From the command line: raise `versionCode`, then run `./gradlew :app:publishReleaseBundle` from `android/`. It goes
  to internal testing; `--track production` needs a person's go-ahead first. This needs a service account with the
  Google Play Android Developer API, invited in Play Console for this app only, and its key at the path in
  `bcs.playKey` in `~/.gradle/gradle.properties`. Without that key the publishing tasks are switched off.
