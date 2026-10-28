# Security policy

Please report vulnerabilities privately through GitHub's **Security → Report a vulnerability** on this
repository rather than in a public issue. Include steps to reproduce and the affected version.

Data handling principles from the PRD (§13.4) apply: user data stays on the device unless sync is
enabled, Google tokens are never exposed to the app UI, exports or logs, and permissions are requested
only when a feature needs them.
