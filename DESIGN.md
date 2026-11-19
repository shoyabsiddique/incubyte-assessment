---
name: ACME Salary Management
description: An operational HR tool built on Angular Material 3 where exact figures, aligned columns and visible change history carry the design.
colors:
  primary: "#005cbb"
  on-primary: "#ffffff"
  primary-container: "#d7e3ff"
  on-primary-container: "#00458f"
  tertiary: "#343dff"
  secondary-container: "#dae2f9"
  error: "#ba1a1a"
  error-container: "#ffdad6"
  on-error-container: "#410002"
  surface: "#faf9fd"
  surface-container-low: "#f4f3f6"
  surface-container-lowest: "#ffffff"
  on-surface: "#1a1b1f"
  on-surface-variant: "#44474e"
  outline: "#74777f"
  outline-variant: "#c4c6d0"
typography:
  headline:
    fontFamily: "Roboto, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 400
    lineHeight: "2rem"
  title:
    fontFamily: "Roboto, sans-serif"
    fontSize: "1rem"
    fontWeight: 500
    lineHeight: "1.5rem"
  body:
    fontFamily: "Roboto, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: "1.25rem"
  label:
    fontFamily: "Roboto, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 500
    lineHeight: "1rem"
rounded:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "32px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label}"
    rounded: "{rounded.lg}"
    height: "40px"
  filter-control:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body}"
    rounded: "{rounded.xs}"
    height: "40px"
  table-frame:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.sm}"
  banner:
    backgroundColor: "{colors.error-container}"
    textColor: "{colors.on-error-container}"
    rounded: "{rounded.sm}"
    padding: "8px 12px"
  notice:
    backgroundColor: "{colors.secondary-container}"
    rounded: "{rounded.sm}"
    padding: "8px 12px"
---

# Design System: ACME Salary Management

## Overview

**Creative North Star: "The Ledger"**

The interface is an instrument for reading and correcting money: precise, orderly and trustworthy. Figures are aligned in columns, units sit next to every amount, dates are written the same way everywhere, and what changed is always visible afterwards. The system is Angular Material 3 with its stock azure palette and Roboto, deliberately unadorned; the work, not the chrome, is the loudest thing on screen.

It is desk-first and dense enough to scan thousands of rows, and it collapses to a drawer navigation on narrow screens as a courtesy rather than an optimised mobile design.

**Key Characteristics:**
- Neutral surfaces, one blue accent used for primary actions, the current navigation item and focus.
- Right-aligned, tabular figures; sentence-case labels; amounts always followed by their currency code.
- One shared page vocabulary: heading, one-sentence intro, filter row, data table, feedback blocks.
- Meaning is never carried by colour alone: deviations carry a sign, statuses carry words.

## Colors

A restrained neutral palette with a single azure accent; the colours are Angular Material 3's system tokens (`--mat-sys-*`) and are not redefined by the application.

### Primary
- **Azure Ink** (#005cbb): filled buttons, the active navigation item, focus rings and selected state. Rare by design.

### Secondary
- **Pale Slate Blue** (#dae2f9): confirmation notices (for example "Salary revised").

### Tertiary
- **Signal Indigo** (#343dff): the "above the median" deviation on the anomalies page. Always paired with a "+" sign.

### Neutral
- **Paper** (#faf9fd): page surface.
- **Mist** (#f4f3f6): toolbar and sidenav, one step below the page.
- **White** (#ffffff): the lightest layer, used for row hover.
- **Ink** (#1a1b1f): body text.
- **Slate** (#44474e): secondary text, hints and table headers.
- **Hairline** (#c4c6d0): 1px borders and dividers; **Stone** (#74777f) for control outlines.

### Error
- **Alarm Red** (#ba1a1a) on **Blush** (#ffdad6): error banners and the "below the median" deviation (always with a "-" sign).

### Named Rules
**The One Voice Rule.** Azure is the only accent. It marks what can be acted on or what is current; it is never decoration.
**The Sign Before Colour Rule.** Anything coloured to mean "above" or "below" must also say it in text.

## Typography

**Font:** Roboto (with a sans-serif fallback), weights 300, 400, 500 and 700, one family for everything.

**Character:** Plain and neutral. A single family keeps labels, buttons and data in one voice.

### Hierarchy
- **Headline** (400, 1.5rem / 2rem): each page's single `h2`.
- **Title** (500, 1rem / 1.5rem): section headings (`h3`).
- **Body** (400, 0.875rem / 1.25rem): tables, forms and prose; prose measure about 72ch.
- **Label** (500, 0.75rem / 1rem): filter labels, table headers, small captions.

### Named Rules
**The Tabular Figures Rule.** Every table and definition list uses tabular numerals so amounts line up and do not jiggle when they change.

## Layout

A fixed 232px sidenav beside a full-width content area; each page owns its padding (16px top, 24px sides, 32px bottom) so a table can use the whole width. Spacing steps are 4, 8, 16, 24 and 32px; groups are tight (4 to 8px), sections are separated generously (24px or more). Below 900px the sidenav becomes an overlay drawer opened from a menu button and closes after navigation; below 600px the signed-in email is hidden. Wide tables scroll inside their own bordered frame and never make the page scroll sideways.

## Elevation & Depth

Surfaces are flat at rest: depth comes from tonal steps (Paper, Mist, White) and 1px Hairline borders, as on the toolbar, sidenav and table frames. The chosen direction is subtle Material shadows (the level 1 and level 2 tokens, soft with a small offset) for surfaces that genuinely float: menus, select panels, dialogs and the overlay drawer. Zero-blur or coloured glows are not part of the system.

## Shapes

Gently rounded: 4px for filter controls, 8px for table frames, banners and notices, 16px for Material buttons. Borders are always 1px. There are no clipped or decorative shapes.

## Components

The component feel is Material as a base with refined, consistent detail. Today most controls are stock Material; distinctive refinements belong in shared styles, never one-off on a page.

### Buttons
- **Shape:** Material 3 rounded buttons (about 16px).
- **Primary:** Filled azure (#005cbb) with white label, used once per view for the main action ("Save changes", "Run simulation").
- **Secondary:** Outlined (stroked) buttons for edit, revise and cancel-style actions; text buttons for back links.
- **States:** Material hover, focus and disabled states; keyboard focus is always visible.

### Inputs / Fields
- **Forms (create, edit, revise):** Material outlined form fields with inline error text under the field and server errors set on the field they concern.
- **Filter rows:** plain native selects and inputs, 40px high, 1px Stone outline, 4px radius; hover darkens the outline, `:focus-visible` shows a 2px azure outline.

### Tables
- **Frame:** 1px Hairline border, 8px radius, scrolls horizontally inside the frame.
- **Header:** Slate text on Mist, 500 weight, no wrapping.
- **Figures:** right-aligned, tabular numerals, currency code after the amount.
- **Rows:** 1px Hairline separators, light hover on the plain tables.

### Navigation
- **Sidenav:** Material list on Mist, the current page marked with the activated state and `aria-current="page"`. On narrow screens it is an overlay drawer behind a menu button that reports expanded or collapsed state.

### Feedback blocks
- **Banner:** Blush background, error-on-container text, 8px radius, for errors that need action, with a retry or reload button inside.
- **Notice:** Pale Slate Blue, same shape, for confirmations (announced with `role="status"`).
- **Empty:** plain Slate text; empty results are worded as "no results found", never as a verdict.

## Do's and Don'ts

### Do:
- **Do** put the currency code after every amount and format with two decimals and thousands separators.
- **Do** keep one vocabulary: the same filter row, table frame, banner and notice on every page.
- **Do** keep keyboard focus visible on every control and link.
- **Do** pair any colour meaning with a sign or a word.

### Don't:
- **Don't** add a second accent colour or use azure as decoration.
- **Don't** use shadows at rest on flat surfaces; reserve them for floating layers.
- **Don't** introduce display fonts, gradients, glows or decorative motion in an operational screen.
- **Don't** style a one-off control on a single page when a shared style exists.
