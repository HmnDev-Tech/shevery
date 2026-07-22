# Contributing Guidelines

Thank you for contributing to the Shevery repository. Please follow these guidelines to ensure a smooth review process.

## Issue Reporting
- **Use Templates:** Use the provided GitHub Issue templates and fill them out completely.
- **Search for Duplicates:** Search open and closed issues before posting. An automated bot flags similar issues.
- **Provide Text Logs:** Provide logs as text in Markdown code blocks (```log). Do not upload screenshots of text logs.
- **Provide Reproduction Steps:** Include clear, step-by-step instructions to reproduce the issue.

## Pull Requests
- **Scope:** Keep PRs focused on a single issue or feature.
- **Compile and Test:** Ensure your code compiles (`./gradlew assembleRelease`) without new warnings.
- **Architecture:** Follow the existing architecture. This fork uses Jetpack Compose, Material 3 Expressive, and targets Android 16/17 SDKs. Use `enableEdgeToEdge()` for window insets.
- **UI Changes:** Include before and after screenshots (or a video) for UI changes.
- **Git History:** Squash commits into logical units with clear messages.

## Code Style
- Follow standard Kotlin idioms.
- Use state hoisting and avoid deep nesting in Compose. Use `ShizukuExpressiveTheme`.
- Run internal linters before submitting.
