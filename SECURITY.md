# Security policy

## Supported versions

Only the latest release published on the [Releases](https://github.com/Sygix/Jauge/releases) page receives security fixes.

## Reporting a vulnerability

Please do not open a public issue or discussion for a security problem.

Report it privately through [GitHub private vulnerability reporting](https://github.com/Sygix/Jauge/security/advisories/new). Include the affected version, the Android version and the steps to reproduce. Do not include personal data such as email addresses, account names, tokens or authorization codes.

Once a fix is released, the advisory is published and you are credited unless you ask otherwise.

## How Jauge handles credentials

OAuth tokens are encrypted with an AES key held in the Android Keystore and never leave the device. Cloud backup and device transfer are disabled. The app only talks to the Anthropic, OpenAI and Nous Research endpoints listed in the README.

## Release integrity

Release APKs are built and signed by GitHub Actions from signed `v*` tags. Only install APKs downloaded from the [Releases](https://github.com/Sygix/Jauge/releases) page of this repository.
