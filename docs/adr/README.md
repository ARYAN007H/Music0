# Architecture decision records

An ADR records a decision that is expensive to reverse, the context that forced
it, and the consequences the project accepts. These records shape the current
codebase most strongly.

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-tauri-2-no-build-frontend.md) | Tauri 2 with a no-build, static frontend | Superseded by [ADR-0005](0005-compose-multiplatform-and-jni-core.md) |
| [0002](0002-separate-app-identity.md) | A separate `musico-online` app identity | Accepted (amended for CMP packaging) |
| [0003](0003-tiered-stream-resolution.md) | Tiered, local-first stream resolution | Accepted (amended by [ADR-0006](0006-loopback-progressive-streaming.md)) |
| [0004](0004-local-first-recommender.md) | A local-only recommender in its own crate | Accepted (amended for `musico_core`) |
| [0005](0005-compose-multiplatform-and-jni-core.md) | Compose Multiplatform UI over a pure-Rust JNI core | Accepted |
| [0006](0006-loopback-progressive-streaming.md) | Progressive playback via a loopback HTTP/1.1 range server | Accepted |

## Format

Each record follows:

```text
# NNNN. Short decision title

- Status: Proposed | Accepted | Superseded by ADR-NNNN
- Recorded: YYYY-MM-DD
- Deciders: maintainers

## Context
## Decision
## Consequences
## Alternatives considered
## References
```

`Recorded` is the date the record was written, not necessarily the date the
decision was first made. When a decision changes, add a new ADR and mark the old
one superseded; do not rewrite history in place.

## When to add one

Add an ADR when a change:

- constrains future code (schema, JNI contract, storage format);
- chooses one of several plausible architectures;
- deliberately accepts a limitation (unsigned desktop builds, no sync, heuristic
  matching);
- would be costly to undo after a release.

Ordinary feature work, bug fixes, and refactors do not need an ADR. If unsure,
ask whether a new contributor would need the rationale six months from now.

## Where to go next

- [ARCHITECTURE.md](../../ARCHITECTURE.md) — the system as it exists.
- [CONTRIBUTING.md](../../CONTRIBUTING.md) — how changes are proposed and reviewed.
- [docs/README.md](../README.md) — the rest of the documentation.
