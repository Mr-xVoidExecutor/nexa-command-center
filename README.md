# NEXA – Personal Command Center (Android)

Personal cyberpunk command center with a voice-reactive, illustrated full-body AI avatar.

**Build:** push this folder to a GitHub repo (branch `main`). The workflow in `.github/workflows/android_build.yml`
builds the APK — download it from the Actions run under *Artifacts → NEXA-debug-apk*.

**First run:** create your access code, open *Settings* and add your keys
(GitHub token, Claude API key, Meta token). Everything is stored encrypted on the device.

Modules: AI Voice, Doc Studio, GitHub, Deploy, Database, Accounts, Meta Ads, Tasks, Notes, Vault, Uptime, Console, Tools, System HUD, Focus, Network, Settings.

**Connected accounts:** Open **Accounts** from the navigation drawer to connect GitHub, Vercel, Netlify and Supabase. Create an access token on each provider's official account page and enter it once; the app validates it before storing it in Android Keystore-backed EncryptedSharedPreferences. Tokens are masked in the UI and can be rechecked or removed. No Gmail or account password is requested or stored. Supabase database access separately needs a project URL and publishable/anon key; never put a `service_role` key in a mobile app. These saved provider tokens enable authenticated account connections; individual Vercel/Netlify management actions are not implemented yet.

**Avatar:** The full-body character is drawn locally in `Avatar.kt`; no avatar service, API key, or network connection is needed. It has idle breathing/blinking, mood colours and expressions, speech-reactive mouth movement, touch-to-wave, drag-to-walk, and IDLE/WALK/RUN/SIT/DANCE/WAVE controls. Avatar movement commands also work from the AI conversation. This is a stylized 2D illustration, not a photorealistic talking-head model or AR character.

**AI memory:** Preferences saved from AI Voice are encrypted on-device. Saved preferences and recent conversation turns are included as context when chatting with the configured AI provider.
