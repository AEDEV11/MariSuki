# MariSuki POS UI and UX guidelines

## Product priorities

- Design first for a landscape Android tablet used during a busy checkout by a non-technical family store operator.
- Keep the interface modern, professional, minimalist, and predictable. Use plain labels and obvious primary actions.
- Maintain emerald-green branding, the MariSuki grocery-bag logo, and Material 3 light, dark, and system theme behavior.
- Inter is bundled in four static weights and is applied to the app typography. Preserve tabular numerals for peso values.

## Adaptive layout

- Use AndroidX window size classes rather than device-name checks.
- Compact widths use bottom navigation. Larger widths use a navigation rail.
- The cashier uses a product catalog with a persistent checkout pane at expanded tablet widths. Compact layouts use an adaptive product grid and a cart bottom sheet.
- Keep products and checkout visible together on a landscape tablet. Preserve usable search, product selection, unit choice, and payment actions at compact widths.
- Use full-screen or scrollable layouts for complex forms where the available width or keyboard requires it. Keep dialogs focused and sized to their content.

## Visual system and accessibility

- Shared spacing and layout tokens are in `ui/theme/DesignTokens.kt`: compact and tablet padding, pane gap, form widths, and a 48 dp minimum touch target.
- Prefer clear hierarchy, readable body text, stable alignment of prices, and concise error/empty states. Avoid unnecessary gaps and oversized dialogs.
- Keep text and controls legible in light and dark schemes. Respect system font scaling and keyboard insets.
- Keep labels localized through Android string resources; Filipino resources live under `values-b+fil`.

## Validation still needed

- Existing screenshots document tablet and one compact cashier viewport, but they are not a complete responsive test. Recheck portrait, compact phone, medium, and expanded widths, plus increased font scale.
- Historical screenshot notes report input-focus ANRs during some dialog/popup transitions under emulator load. Reproduce on a less-loaded emulator or physical tablet before changing focus behavior.
- Verify the full setup flow, every complex form and popup, and final-build post-login navigation. Do not treat a rendered screenshot as interaction coverage.
