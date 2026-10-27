# NEXA – Personal Command Center (Android)

Personal cyberpunk command center with a voice-reactive, illustrated full-body AI avatar.

**Build:** push this folder to a GitHub repo (branch `main`). The workflow in `.github/workflows/android_build.yml`
builds the APK — download it from the Actions run under *Artifacts → NEXA-debug-apk*.

**First run:** create your access code, open *Settings* and add your keys
(GitHub token, Claude API key, Meta token). Everything is stored encrypted on the device.

Modules: AI Voice, Doc Studio, GitHub, Deploy, Database, Accounts, Meta Ads, Tasks, Notes, Vault, Uptime, Console, Tools, System HUD, Focus, Network, Settings.

**Connected accounts:** Open **Accounts** from the navigation drawer to connect GitHub, Vercel, Netlify and Supabase. Create an access token on each provider's official account page and enter it once; the app validates it before storing it in Android Keystore-backed EncryptedSharedPreferences. Tokens are masked in the UI and can be rechecked or removed. No Gmail or account password is requested or stored. Supabase database access separately needs a project URL and publishable/anon key; never put a `service_role` key in a mobile app. These saved provider tokens enable authenticated account connections; individual Vercel/Netlify management actions are not implemented yet.

**Permission registry setup (managed, opt-in client apps):**

1. Configure the Supabase project HTTPS URL and publishable/anon key in NEXA's **Accounts → Supabase Database Project** panel. Enable email/password sign-in for the intended Supabase users.
2. Link this repository to the Supabase project and apply `supabase/migrations/202610270001_permission_registry.sql` (`supabase link --project-ref <project-ref>` then `supabase db push`).
3. Ensure the `SUPABASE_SERVICE_ROLE_KEY` is available only as a server-side Edge Function secret in the Supabase dashboard, then deploy `supabase functions deploy permission-registry`. Never put this service-role key in NEXA, another mobile app, Git, a command-line history, or chat. The function checks Supabase user JWTs for owner actions and validates per-install tokens for client sync; its gateway JWT check is disabled intentionally because client installs use one-time app tokens.
4. Open **Permission Registry** in NEXA and sign in to the Supabase account. The email/password is not persisted; the refresh session is encrypted with Android Keystore-backed EncryptedSharedPreferences. Generate a one-use enrollment code (10-minute expiry).
5. In each app you own/manage, show a clear permission-monitoring consent screen and collect the code only after the user opts in. Embed the `PermissionRegistryClient` helper from `app/src/main/java/com/nexa/app/PermissionRegistry.kt`, initialize it with the Supabase project URL and public publishable/anon key, then call `enroll(code)`. Keep the returned client ID for display; the per-install client token is stored inside that client app's encrypted preferences.
6. Call `recordPermissionRequest(permission, granted)` from the owning app's runtime permission result callback, then call `sync()` after opt-in, after permission results, and when the app returns to the foreground. For background monitoring, schedule a WorkManager job with a conservative interval and user-visible disclosure; Android's periodic WorkManager interval is at least 15 minutes. The NEXA dashboard polls the owner's RLS-protected snapshots every five seconds and indicates when the client last synced.
7. Let the client user revoke sharing from that app; `revoke()` disables its server credential and clears its local enrollment. The NEXA owner can also revoke an enrolled client from the dashboard.

Only manifest-declared permissions in the helper catalog are reported. Runtime-denied vs not-yet-requested depends on the client app recording its own permission request results. Android does not notify another app of permission changes in real time; a client must sync on foreground/request events or via disclosed periodic work. No SMS, contact, location, microphone, or other protected content is collected—only permission names, categories, current grant states, package/app names, and sync timestamps.

**Avatar:** The full-body character is drawn locally in `Avatar.kt`; no avatar service, API key, or network connection is needed. It has idle breathing/blinking, mood colours and expressions, speech-reactive mouth movement, touch-to-wave, drag-to-walk, and IDLE/WALK/RUN/SIT/DANCE/WAVE controls. Avatar movement commands also work from the AI conversation. This is a stylized 2D illustration, not a photorealistic talking-head model or AR character.

**AI memory:** Preferences saved from AI Voice are encrypted on-device. Saved preferences and recent conversation turns are included as context when chatting with the configured AI provider.
