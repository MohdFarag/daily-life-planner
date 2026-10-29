# Security policy

This repository is private. Report a vulnerability directly to the repository owner rather than in an
issue or pull request, with steps to reproduce and the affected version. (GitHub's private vulnerability
reporting form is only available on public repositories.)

Dependabot alerts and security updates are enabled for dependencies.

Data handling principles from the PRD (§13.4) apply: user data stays on the device unless sync is
enabled, Google tokens are never exposed to the app UI, exports or logs, and permissions are requested
only when a feature needs them.
