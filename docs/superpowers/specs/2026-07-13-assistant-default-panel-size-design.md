# Assistant Default Panel Size Design

## Goal

Make the desktop furniture assistant open at the size shown in the approved reference: a wide right-side panel that nearly fills the viewport height, while preserving the existing draggable and resizable behavior.

## Desktop behavior

- Use a default panel width of `656px`.
- Place the default panel `24px` from the right edge.
- Leave `24px` above and below the panel, making the default height `calc(100dvh - 48px)` in visual terms.
- Clamp width and height to the available viewport so the panel never overflows on narrower or shorter desktop screens.
- Keep the existing drag and eight-direction resize interactions unchanged after opening.
- Reset to this approved default size each time the closed panel is opened, matching the current reset-on-open behavior.

## Responsive behavior

- Keep the existing mobile bottom-sheet layout at viewport widths of `640px` and below.
- Preserve existing minimum dimensions and viewport margins for intermediate desktop sizes.

## Implementation boundary

Change only the default desktop panel dimensions and their related regression assertions. Do not alter chat content, product cards, API behavior, launcher placement, colors, typography, or mobile composition.

## Verification

- Add a failing regression assertion for the new `656px` default width and viewport-derived default height.
- Verify the focused panel tests pass after the implementation.
- Run the complete frontend test suite and production build.
- Confirm the panel opens at the approved size at a `1920x908` desktop viewport and remains contained at narrower desktop sizes.
