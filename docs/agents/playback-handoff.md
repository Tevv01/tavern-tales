# Handoff: Playback (spec #2)

Delete this file in the merge that closes #2.

The design is settled; don't re-litigate it. Everything decided is in:

- **Spec:** GitHub issue #2, "Playback: sound and lights stop independently, with a single Playback module".
- **Tickets:** seven sub-issues of #2. Read their "Blocked by" sections:
  1. Stop lights always stops the movement (no blockers)
  2. Playback replaces the scene launcher (no blockers)
  3. Stop ends only the sound; the lit scene and Stop lights in the lights row (blocked by 1, 2)
  4. Lights only: Apply and previews (blocked by 3)
  5. One now-playing card (blocked by 3)
  6. Deleting a scene ends its sound and lights (blocked by 3)
  7. Lights only in the background and in the media controls (blocked by 3)
- **Vocabulary:** `GLOSSARY.md` (Playback, sound scene, lit scene, movement).
- **The key decision:** `docs/adr/0001-sound-and-lights-stop-independently.md`. Stop ends only the sound, and the lights keep moving on purpose.

## How to proceed

- **This branch, `feature/playback`, is the integration branch.** Ticket 2's "commit the glossary and ADR" is already done here.
- **Start with `/implement-spec #2`.** Tickets 1 and 2 are the first frontier.
- **The "checked on the phone" acceptance items are the user's to confirm** with the debug build. Follow CLAUDE.md's Workflow, and never use `connectedDebugAndroidTest`.
- **Ticket bodies refer to blockers as "Ticket N: title"**, because they were pasted by hand before the issue numbers existed. Map them to the real sub-issue numbers.
