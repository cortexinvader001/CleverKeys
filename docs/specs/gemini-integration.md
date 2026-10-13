# Gemini integration for CleverKeys

## Goal

Add an optional, explicit opt-in cloud assistant to the CleverKeys keyboard so the user can ask Gemini for help while typing and optionally include clipboard text. The feature must be disabled by default and must never send clipboard data without an explicit user action.

## Scope

- Add a Gemini button to the keyboard UI only when the feature is enabled in settings.
- Offer an explicit consent flow before sending clipboard content.
- Add a secure API key store.
- Add Pollination fallback handling when Gemini returns a rate limit / timeout / auth / network failure.
- Keep the app local-first and transparent: the user must know a cloud request is being made.

## Privacy contract

- Default: off.
- Clipboard is only included when the user taps a button or confirms a clipboard action.
- Clipboard preview is shown to the user before send.
- Sensitive text can be filtered or skipped.
- Failed or rate-limited requests should not silently retry multiple times.

## Fallback path

When Gemini fails with a 429, timeout, auth error, or other network failure, the app should use the best-effort fallback endpoint below:

- `https://text.pollination.ai/`

This is a graceful fallback route for the keyboard app. The production implementation should keep any request amount conservative and gate the fallback behind the same user consent flow.

## Settings to add

- `gemini_enabled` (default false)
- `gemini_api_key` (encrypted storage)
- `gemini_include_clipboard` (default false)
- `gemini_confirm_before_send` (default true)
- `gemini_use_pollination_fallback` (default true)

## UX flow

1. User taps Gemini button in keyboard toolbar.
2. Keyboard opens a small dialog / bottom sheet.
3. User can type a prompt or choose a clipboard helper action.
4. Clipboard preview is shown only if enabled.
5. User confirms send.
6. Response is streamed or returned.
7. User taps Insert or Copy.

## Implementation notes

- Prefer `HttpURLConnection` or OkHttp with `application/json` requests.
- Keep request bodies small and redacted.
- Strip obvious secrets before sending clipboard content.
- If the app is in a blind/remote editor, do not let Gemini send the payload automatically.

## Acceptance criteria

- Gemini is off by default.
- API key is stored in secure storage, not plain SharedPreferences.
- User sees a consent step before clipboard is included.
- Rate-limited Gemini requests automatically fall back to Pollination.
- App does not silently send clipboard data in the background.
