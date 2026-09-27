## 2024-05-19 - Semantic Buttons over Custom Components
**Learning:** Found several hand-rolled `Box` components acting as buttons (e.g. FAB and header icon buttons). This approach frequently lacks proper accessibility (no `contentDescription`, bad semantics) and can cause state bugs like animations getting stuck in a "pressed" state since they aren't properly managed.
**Action:** Always prefer standard Material 3 components like `FloatingActionButton` and `IconButton`. They provide built-in a11y, ripple interactions, state handling, and proper sizes out-of-the-box.
