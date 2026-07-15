# Furniture Lite AI Menu Design

## Goal

Keep the Oakved furniture-lite admin experience while making the complete AI menu tree visible to authorized users. BPM, CRM, IoT, MES, DIY, code generation, and job modules remain hidden.

## Scope

This change affects only admin frontend menu and fixed-route filtering.

In scope:

- Restore the explicit AI route allowlist in `src/config/furnitureLite.ts`.
- Remove `/ai` from the fixed-route deny list.
- Update the furniture-lite configuration contract so it requires AI access instead of requiring AI to be hidden.
- Run the AI access contract, furniture-lite contract, and frontend build.

Out of scope:

- Adding `yudao-module-ai-server` to the monolithic `yudao-server` runtime.
- Configuring AI provider API keys or models.
- Exposing any non-AI module currently hidden by furniture-lite mode.

## Design

Use an explicit allowlist for AI routes rather than disabling furniture-lite mode or permitting every route by prefix. The allowlist will cover the existing AI console, chat, model, knowledge, workflow, writing, image, music, and mind-map routes already present in the admin frontend.

The fixed-route deny list will continue to block `/bpm`, `/crm`, `/iot`, `/mes`, `/diy`, `/codegen`, and `/job`, but will no longer block `/ai`.

The existing `scripts/check-furniture-lite-ai-access.mjs` contract is the primary regression test. The older furniture-lite contract currently asserts that AI must be hidden; it will be updated to assert the approved AI allowlist while retaining checks for the other hidden modules.

## Behavior

When `VITE_ADMIN_MODE=furniture-lite`:

- Authorized AI menus returned by the backend remain in the generated menu tree.
- AI fixed routes are registered.
- Existing furniture commerce, member, payment, infrastructure, and system menus continue to work.
- BPM, CRM, IoT, MES, DIY, code generation, and job routes remain filtered out.

When furniture-lite mode is disabled, existing full-admin behavior remains unchanged.

## Verification

1. Run the AI access contract before implementation and confirm it fails because AI routes are not allowed.
2. Restore the explicit AI route allowlist and remove `/ai` from the deny list.
3. Update the general furniture-lite contract to match the approved behavior.
4. Run both furniture-lite contracts and confirm they pass.
5. Run the admin frontend build.
6. Start the admin frontend and verify the AI menu is visible after login while other denied modules remain hidden.

## Known Follow-up

Showing the menu does not add AI controllers to the monolithic backend. AI API availability in the single-process `yudao-server` is a separate backend integration task.
