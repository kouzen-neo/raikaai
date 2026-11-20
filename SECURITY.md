# Security policy

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Report privately through GitHub: open the repository's **Security** tab and choose
**Report a vulnerability**. Include the app version (Settings → About), your Android version,
steps to reproduce, and what an attacker could do.

You will get a reply as soon as possible. Once a fix is released, the report can be made
public with credit to you, if you want.

## Supported versions

Only the latest release receives security fixes.

## In scope

RaikaAI runs code and listens on the network, so these areas matter most:

- **Local API server** (`/v1`): authentication, the browser-origin guard, exposure on the LAN.
- **Linux environment**: escaping the PRoot sandbox, the permission gate (Ask / Auto / Full
  access), the destructive-command policy, phone storage access (`/sdcard`).
- **Agent tools and MCP**: a model or a malicious web page / MCP server getting commands run
  without approval, prompt injection that bypasses the approval dialog.
- **Secrets**: API keys, tokens and bearer tokens stored with the Android Keystore.
- **Characters catalog**: bypassing the database rules (reading unapproved characters, editing
  or deleting others, skipping the rate limit).

## Out of scope

- A model giving wrong or unsafe answers on its own (that is model behaviour, not a vulnerability).
- Commands you approve yourself, or anything allowed by **Full access** after confirming.
- Attacks that need a rooted phone or physical access to an unlocked device.
- The Supabase publishable key in the source code; it is public by design and limited by
  row-level security.
