# Conventions Memory

## Purpose

Capture stable repository conventions that are broader than one feature and narrower than global policy.

## Suggested categories

- Naming and folder structure
- Testing style
- Review expectations
- Documentation discipline
- Release hygiene

## 2026-05-14 UI implementation visual QA gate

- For frontend or mobile UI work, split the work into a developer pass and a QA pass before calling it done.
- Developer pass: reason through layer stacking, fixed overlays, scroll containers, z-index, responsive breakpoints, and the target visual contract before editing.
- QA pass: verify the actual rendered layout with the strongest available tool, such as emulator/simulator, in-app browser, screenshot inspection, Playwright, or computed layout checks.
- Completion is not credible if the primary screen was implemented only from code intuition and no rendered layout evidence was checked.
