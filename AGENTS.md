# Instructions for project work

Before making changes in this repository, read `PROJECT_HANDOFF.md` for the current status, active branch, constraints, and next step.

- Keep Android ZIM prototype work on `feature/android-zim-probe` unless the user explicitly chooses another branch.
- Do not modify the existing PWA files, merge branches, create a pull request, publish a release, or deploy without the user's explicit instruction.
- The user performs tests on their phone. Do not claim device behavior until the user reports it.
- Before asking the user to install a build, explain whether it reuses or separates app data, permissions, and the local ZIM index.
- Update `PROJECT_HANDOFF.md` when a material code, build, or phone-test milestone changes.
- Whenever a task changes files, finish with a prominent status in the conversation saying whether those changes are saved on GitHub. When allowed by the project boundaries, commit and push the requested changes to the active branch as part of the task.
- Say “saved on GitHub” only after confirming the push succeeded; include the branch, commit, and changed filenames. If changes remain local or the push fails, state clearly that they are not yet saved on GitHub and why.
- Report GitHub backup, CI/build status, and phone-test status separately so one is never mistaken for another.
