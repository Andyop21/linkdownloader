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
| [BUG-001](bugs/BUG-001-finish-prompt-rewrites-examples.md) | `finish-prompt.sh` rewrites the example lines that name the finished prompt |

## Decisions

| ID | Title |
|---|---|
| [DEC-001](decisions/DEC-001-kotlin-compose-ytdlp.md) | Kotlin, Jetpack Compose and youtubedl-android |
| [DEC-002](decisions/DEC-002-no-ffmpeg-merge-with-mediamuxer.md) | No ffmpeg; pick formats in Kotlin and join them with MediaMuxer |
| [DEC-003](decisions/DEC-003-engine-probe-results.md) | Engine probe results and the picker rules for P03 |
| [DEC-004](decisions/DEC-004-serialization-and-coroutines.md) | kotlinx.serialization for yt-dlp's JSON, kotlinx.coroutines declared directly |
