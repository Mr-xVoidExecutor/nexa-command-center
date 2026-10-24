# NEXA – Personal Command Center (Android)

Cyberpunk command center with a voice-talking AI avatar.

**Build:** push this folder to a GitHub repo (branch `main`). The workflow in `.github/workflows/android_build.yml`
builds the APK — download it from the Actions run under *Artifacts → NEXA-debug-apk*.

**First run:** create your access code, open *Settings* and add your keys
(GitHub token, Claude API key, Meta token). Everything is stored encrypted on the device.

Modules: AI Voice, Doc Studio, GitHub, Deploy, Database, Meta Ads, Tasks, Notes, Vault, Uptime, Console, Tools, System HUD, Focus, Network, Settings.

**Mood images:** `res/drawable-nodpi/nexa_<mood>.jpg` (happy, angry, sad, shy, playful, caring) are colour-graded variants of the base image.
Replace any of them with real expression artwork of the same character (same file name) and the app will use it automatically.
