# 2026-05-11 Low-Vision Terms TalkBack Bug

- Symptom: In the low-vision terms guide, turning on TalkBack made the agreement card impossible to advance with the expected double-tap activation.
- Root cause: `TermsGuideScreen` handled the main card only with `pointerInput { detectTapGestures(onDoubleTap = ...) }` and set a button role manually. TalkBack double-tap invokes the node's semantics click action, but the card had no `clickable`/`onClick` semantics action to execute.
- Fastest reproduction path: navigate to the low-vision terms guide with TalkBack enabled, focus the yellow agreement card, and double-tap. The announcement is present, but the step does not advance.
- Durable fix: expose the main card action through Compose `clickable(role = Role.Button, onClickLabel = cardA11y, onClick = onAdvance)` and keep the content description separate.
- Regression test or alert that should exist: `TermsGuideScreenTest.main agreement card exposes semantic click action for talkback activation` should fail if the card regresses to pointer-only gesture handling.
