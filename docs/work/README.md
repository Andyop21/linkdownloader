# Work records

Three kinds of record, each a Markdown file made from its template.

| Folder | Holds | File name | Template |
|---|---|---|---|
| [bugs/](bugs/) | Defects, open and fixed | `BUG-NNN-<slug>.md` | [templates/bug.md](templates/bug.md) |
| [decisions/](decisions/) | Choices worth remembering: stack, libraries, behaviour trade-offs | `DEC-NNN-<slug>.md` | [templates/decision.md](templates/decision.md) |
| [handoffs/](handoffs/) | The end-of-session report of each prompt session | `P<NN>-<slug>.md` (the prompt's ID) | [templates/handoff.md](templates/handoff.md) |

Numbers are three digits, assigned in order, never reused. A bug stays in `bugs/` when fixed; its
`status` changes to `fixed` and it names the fixing commit.

## Open bugs

| ID | Title |
|---|---|

## Decisions

| ID | Title |
|---|---|
| [DEC-001](decisions/DEC-001-kotlin-compose-ytdlp.md) | Kotlin, Jetpack Compose and youtubedl-android |
