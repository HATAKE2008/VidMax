## 2024-09-26 - [Add accessible descriptions to empty state icons]
**Learning:** Empty states often use large, decorative-appearing icons that developers assume don't need `contentDescription`s. However, these provide critical context for screen reader users when there's no actual content in the view.
**Action:** Always check `Icon` usage in empty state screens (e.g. `isEmpty()` blocks) to ensure they have descriptive text instead of `null`, so screen readers can convey the state of the app visually.
