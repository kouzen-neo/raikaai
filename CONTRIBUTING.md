# Contributing to RaikaAI

Thanks for helping. Bug reports, fixes, docs and features are all welcome.

## Before you start

- For anything bigger than a small fix, open an issue first so we can agree on the approach.
- Read [AGENTS.md](AGENTS.md). It holds the project's conventions, and it is what AI coding
  agents follow too, so humans and agents work the same way.
- Security problems go through [SECURITY.md](SECURITY.md), not public issues.

## Setup

Requirements: JDK 17+, the Android SDK (see [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)) and an
arm64 Android phone (8.0+) for testing. The bundled Linux environment is arm64 only, so x86
emulators cannot run it.

```bash
git clone https://github.com/<you>/raikaai.git
cd raikaai
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
```

Rust is only needed if you change the PTY helper in `native/raikapty` (see its `build.sh`).

## Making a change

1. Branch from `main`.
2. Keep the change focused. English for code, comments, UI text and docs.
3. Add or update unit tests for logic you touch (`app/src/test`).
4. Make sure `compileDebugKotlin` and `testDebugUnitTest` pass.
5. Update `README.md` / `docs/` when behaviour changes, and add a line under an
   `## [Unreleased]` heading in `CHANGELOG.md`.
6. Test on a real phone when you change UI, the terminal, or the agent loop.

## Commits and pull requests

- Commit subjects like `feat(chat): …`, `fix(terminal): …`, `docs: …`. Explain the why in the body.
- No `Co-Authored-By` or AI attribution lines.
- Never commit secrets (`local.properties`, keystores, API keys, tokens).
- Fill in the pull request template: what changed, why, and how you tested it. Screenshots help
  for UI changes.

## License

By contributing you agree that your work is licensed under the
[GNU AGPL v3.0](LICENSE), like the rest of the project.
