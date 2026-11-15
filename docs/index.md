# Documentation

Short on purpose. Read this page, then only what your task needs.

| Document | Read it when |
|---|---|
| [product.md](product.md) | You change what the app does or how it looks. It is the source of truth for behaviour. |
| [architecture.md](architecture.md) | You add or move code, add a dependency, or touch the download path. |
| [development.md](development.md) | You need to build, test, run or install the app. |
| [definition-of-done.md](definition-of-done.md) | Before you call any task finished. |
| [git-workflow.md](git-workflow.md) | Before your first commit. |
| [work/](work/README.md) | You file a bug, record a decision, or write a handoff. |
| [../prompts/](../prompts/README.md) | You run or write a session prompt. |

## Task router

| Task | Read |
|---|---|
| Change the screen, its text or its look | product.md, architecture.md § *Code layout* |
| Change link handling or the download | product.md § *Behaviour*, architecture.md § *Download flow* |
| Add or upgrade a dependency | architecture.md § *Dependencies*, then record a decision |
| Fix a bug | its file in `work/bugs/`, then the code it names |
| Run a prompt | `../prompts/session-protocol.md`, then the prompt |

## Keeping docs true

A commit that changes behaviour, structure or commands updates the matching document in the same
commit. A document that disagrees with the code is a bug: fix it or file it.
