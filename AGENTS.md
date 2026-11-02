# Project workflow

- After each completed requested update, run the relevant checks, commit all source and documentation changes with a descriptive commit message, and push the current branch to its origin remote.
- After each completed requested update, always generate a signed release APK with `:app:assembleRelease`, verify the APK exists, and include its local path in the completion response. Do not substitute a debug APK. If release signing credentials are unavailable, report the release build as blocked. Keep the APK and signing credentials out of Git.
- Include the verified commit or push status in the completion response. If a push fails, report it clearly.
- Keep signing keys, passwords, local configuration, generated APKs, build output, emulator backups and temporary files out of Git.
- Reuse the existing local release signing key across builds and devices. Never regenerate or overwrite signing material; if it is missing, report signing as blocked.
