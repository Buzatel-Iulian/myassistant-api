# myassistant-api

The REST API of **MyAssistant**: Spring Boot 4 on Java 25 (Gradle), with MongoDB. It stores user accounts, prompt templates and LLM configurations, secured with per-user JWT, and documents itself on Swagger UI. It never sees prompts or answers: inference runs in the browser.

- What is built and what is in scope now: [`docs/requirements.md`](../docs/requirements.md) in the wrapper repository `myassistant` (the source of truth).
- Data model and endpoints: [`docs/architecture.md`](../docs/architecture.md) in the wrapper.
- Conventions for humans and coding agents: [`AGENTS.md`](AGENTS.md) (`CLAUDE.md` is generated from it with `sh scripts/sync-agent-instructions.sh`).

The two `../docs/...` links are relative to the layout `make bootstrap` creates (this repository cloned inside the wrapper `myassistant`). Viewed on its own, for example on a Git host, open the same paths in the `myassistant` wrapper repository.

This repository is cloned next to the wrapper's `Makefile` by `make bootstrap`; run it with `make up` from the wrapper.
