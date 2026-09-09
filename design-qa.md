# CMS code-management visual QA

Source: D:/furniture web2b/design-previews/cms-code-management-2026-09-09/cms-code-management-preview-v1.png
Initial capture: D:/furniture web2b/work/cms-preview/implementation-before.png
Viewport: 1488 × 1058 CSS pixels; images both 1488 × 1058, density 1.
State: Header enabled, sample code saved as a draft. Sample code exists only in the disposable local fixture.

Initial findings:
- P2: Placement description and toggle used two rows, pushing the editor down. Fix: align both in one desktop row.
- P2: Editor was too short and the supporting column too narrow. Fix: 506 px editor, 330 px supporting column.
- P2: Insertion positions were merged into the explanation card. Fix: separate card as shown in the approved image.
- P2: Code text was too small and weakly highlighted. Fix: 14 px monospace and register JavaScript syntax highlighting.

The surrounding admin shell in this fixture is for context; existing production shell behavior is preserved.
Final capture: D:/furniture web2b/work/cms-preview/implementation-final-desktop.png
Mobile capture: D:/furniture web2b/work/cms-preview/implementation-mobile.png
Mobile history: D:/furniture web2b/work/cms-preview/history-mobile.png
Runtime evidence: D:/furniture web2b/work/cms-preview/runtime-checks.png

The source and final desktop images were opened together and inspected at full resolution at the same 1488 × 1058 viewport and density 1. All initial P2 findings above are resolved.

Final review:
- Editor, tabs, card boundaries, footer actions and supporting information hierarchy follow the approved composition.
- Navy navigation, blue actions, white panels and gray canvas match the existing admin.
- Code is readable at 14 px monospace; the highlight overlay and input use identical font and line height.
- Intended differences: existing production shell controls are preserved; no design-preview badge or seeded production sample. Actual site URL, draft/published state, code count, consent and raw-HTML verification guidance are displayed.
- At 390 × 844 CSS pixels the layout becomes one column. With the 15 px scrollbar, the content width is 375 px; scrollWidth equals clientWidth. The history dialog also fits without page overflow.
- Save, publish, history, restore-to-draft, dirty tenant-switch cancellation and an independent empty tenant were tested using the actual component with a disposable fixture API. This did not contact production ERP. Backend tenancy and publishing semantics were tested separately.
- Browser runtime passes all 13 checks, including nonce, ordering and withdrawal for embedded resources. No advertising endpoint was contacted.
- No unresolved P0, P1 or P2 visual differences. This report covers local visual acceptance, not a production deployment smoke test.

final result: passed
