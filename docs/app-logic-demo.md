# Stop the World Demo Logic

This demo intentionally starts with a simple product simulation before real system-level blocking.

## Current demo flow

1. Show MVP goals.
2. Provide a button to open Android Usage Access settings.
3. Show a sample restricted app rule.
4. Show the first-open intervention page:
   - app name
   - custom reminder
   - intent chips
   - countdown friction
   - "do not open" and "continue 5 minutes" actions

## Current rule-engine priority

1. No rule or disabled rule: allow.
2. Forced rest active: block.
3. Daily time limit reached: block.
4. Daily open-count limit reached: block.
5. Unlock session active: allow.
6. Otherwise: show delay page.

## Next implementation steps

- Read installed applications.
- Implement UsageStatsManager repository.
- Persist AppRule via DataStore or Room.
- Replace demo state with real daily aggregates.
- Add AccessibilityService only after permission/policy copy is finalized.
