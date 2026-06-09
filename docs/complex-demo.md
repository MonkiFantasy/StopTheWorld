# Complex Demo: Usage Stats + Real App Reminder

This demo covers the first real product loop:

1. User grants Usage Access.
2. The app reads today's foreground usage with `UsageStatsManager`.
3. The user picks one real installed app as the restricted app.
4. The user enables the demo Accessibility Service.
5. When that app moves to the foreground, Stop the World opens a pause/reminder screen.
6. The user either returns home or waits and unlocks the app for 5 minutes.

## How to test on device

1. Install the debug APK.
2. Open **时停 Demo**.
3. Tap **Usage Access** and allow usage access for the app.
4. Return to the app and tap **刷新统计/规则**.
5. Select one app from **今日常用 App** or **可启动 App**.
6. Tap **无障碍服务** and enable **时停 Demo**.
7. Open the selected app.
8. A pause screen should appear with:
   - the app name
   - mindful prompt
   - intent chips
   - countdown
   - "不打开了" and "继续 5 分钟"

## Privacy boundary

The demo Accessibility Service only reads the foreground package name from window-change events. It sets `canRetrieveWindowContent=false` and does not read, store, or upload screen text, chat content, input content, or passwords.

## Current limitations

- Only one restricted app is stored.
- Rules are simple SharedPreferences, not Room/DataStore yet.
- Unlock duration is fixed at 5 minutes.
- Reminder delay is fixed at 10 seconds.
- UsageStats open-count is approximate and event-based.
- OEM background restrictions may affect behavior.
