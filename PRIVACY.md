# Privacy

RaikaAI has no account, no analytics and no tracking. The developer receives no data from the
app. What leaves your phone depends only on the features you use.

## Stays on your phone

- Chats, characters you keep, settings, Raika's memory (`SOUL.md`, `AGENTS.md`, `MEMORY.md`).
- Everything sent to an **on-device model**: prompts, images, audio, documents and replies.
- The Linux environment and its files (`/workspace`, `/root/.raika`).
- API keys and tokens, encrypted with the Android Keystore.

## Sent only when you use the feature

| Feature | What is sent | To whom |
|---|---|---|
| Remote models | Your messages, attachments and the system prompt (including Raika's memory) | The provider you chose (OpenAI, Anthropic, Google, OpenRouter, …), under their privacy policy |
| Web search / link summary | The search query or the link | The search service or website |
| Model downloads | Download requests (and your Hugging Face token for gated models) | Hugging Face |
| Characters → Discover | Catalog requests and view counts | The project's Supabase database |
| Publishing a character | The character's name, description, greeting, prompt and avatar link | The project's Supabase database, public after review |
| MCP servers | Tool arguments you approve | The MCP server you added |
| Linux environment | Whatever the commands you run or approve request | The hosts those commands contact |
| Local API server | Nothing goes out; devices on your Wi-Fi can reach the phone while it is on | — |

## Permissions

- **Microphone**: voice input, only while you dictate or record.
- **Notifications**: reminders and the "working in background" notice.
- **All files access** (optional): only if you turn on *Phone storage in Linux*.
- **Internet**: remote models, downloads, search, the characters catalog.

## Deleting your data

- Delete chats in the app, or clear everything in **Settings → Data & Storage**.
- Uninstalling the app removes all of its data from the phone.
- Published characters can be removed by opening an issue with the character's name.
