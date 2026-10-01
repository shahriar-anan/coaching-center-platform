# Student activation re-issue

Date: 2026-09-26

## Why

Offline onboarding creates a student as `PENDING_ACTIVATION` and shows the activation code once. If an admin leaves that screen without copying the code, the student cannot be activated. Both admin roles can already call `POST /students/{id}/activation-code`.

## Done

- A `PENDING_ACTIVATION` student row shows "New activation code" for Master Admin and System Admin.
- The new code appears once in the same copyable panel used on create. Leaving the list drops it.
- Active and inactive rows have no re-issue action.
- Delete stays Master Admin only and is absent for System Admin.

## Tested

Not run in the browser in this change. Refresh the students list and use a pending student to confirm the button and the one-time code panel.
