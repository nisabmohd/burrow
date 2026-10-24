---
name: build-screen
description: Build one Burrow screen from its design PNG so it matches exactly. Use when implementing or redoing any screen listed in docs/SCREENS.md.
---

# Build a screen from its PNG

1. Open the PNG (`design/screens/NN-name.png`, light; check dark in the app) and its row in `docs/SCREENS.md`. List the blocks top to bottom.
2. Map every block to a component in `docs/COMPONENTS.md`. Typical shapes:
   - tab screen: top bar (headline + TonalIconButtons) → Note? → content → FAB
   - list/summary screen: CenterHeader → SectionHeader + ListGroup(ListRow…) → FAB
   - detail screen: back + one action → CenterHeader → rows → one bottom action
   - settings: plain ListGroups with switches/chevrons, no hero, no explanations
3. Write the UI state first (immutable data class), then the ViewModel (StateFlow from Room Flows), then the composable. Previews for light and dark with fake state.
4. Numbers: amounts formatted per locale; never coloured except "over" in error.
5. Motion: the NavHost already gives shared axis; add only what MOTION.md lists for this screen (count-up, bars).
6. Empty and edge states from the "Edge cases" list in SCREENS.md — every screen has its empty state.
7. Run on a device at 412 dp: compare side by side with the PNG (spacing, sizes, colours, text). Then dark, 200% font, 360 dp width.
8. Commit "Screen: <name> (NN)" and tick it in `TODO.md`.
