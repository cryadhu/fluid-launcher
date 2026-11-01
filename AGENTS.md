# Project workflow

- After each completed requested update, run the relevant checks, commit all source and documentation changes with a descriptive commit message, and push the current branch to its origin remote.
- After each completed requested update, generate an installable debug APK with `:app:assembleDebug` (or the requested build variant), verify the APK exists, and include its local path in the completion response. Keep the APK out of Git.
- Include the verified commit or push status in the completion response. If a push fails, report it clearly.
- Keep signing keys, passwords, local configuration, generated APKs, build output, emulator backups and temporary files out of Git.
