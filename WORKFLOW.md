# Secure AI-Assisted Development Workflow

This document describes the three-phase governance workflow added to LAPS to ensure every code change is grounded in structured requirements, planned before implementation, and security-reviewed before commit.

---

## Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│  PHASE 1: Structured Requirements                                   │
│  GitHub Issue (feature_request.yml) → triage check (issue-triage)  │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ issue approved into backlog
┌──────────────────────────▼──────────────────────────────────────────┐
│  PHASE 2: Planning Before Code                                      │
│  /plan <N> → implementation plan → human approval → code changes   │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ code written
┌──────────────────────────▼──────────────────────────────────────────┐
│  PHASE 3: Automated Security Review                                 │
│  /security-review → APPROVED → git commit → hook (Semgrep) → merge │
└─────────────────────────────────────────────────────────────────────┘
```

All three phases are required. No code may be committed without completing each step.

---

## Phase 1 — Structured Requirements

**Goal:** Force security thinking before any work enters the sprint backlog.

### How it works

1. A developer opens a new GitHub issue using the **Feature Request** template.
2. The template requires all fields to be filled in, including security considerations and trust boundaries.
3. A GitHub Actions workflow automatically checks whether at least one trust boundary has been selected.
4. If no trust boundary is checked, the issue is labelled `blocked-missing-security-analysis` and a comment is posted. The issue cannot enter the backlog until the author corrects it.
5. Once all fields are complete and at least one trust boundary is checked, the issue is eligible for backlog.

### Fields required by the issue template

| Field | Purpose |
|-------|---------|
| **Summary** | User story in GIVEN/WHEN/THEN or "As a [role], I need..." format |
| **Acceptance Criteria** | Binary pass/fail statements that become the test contract |
| **Security Considerations** | ASVS control mappings (V2 Auth, V4 Access Control, V5 Validation, V7 Error Handling, V8 Data, V13 API) |
| **Trust Boundaries Affected** | Checkboxes for every boundary the feature crosses (e.g., Employee → App, App → DB) |
| **Input / Output Contracts** | Field names, types, max lengths, server-side validation rules per boundary |
| **Testability Criteria** | Named test methods, MockMvc scenarios, security negative tests |
| **Data Flow** | ASCII text diagram: Actor → Controller → Service → Repository → DB → (Email) |

### Trust boundary enforcement

The `issue-triage.yml` workflow runs on every issue open and edit:
- Checks for `- [x]` (a checked checkbox) in the issue body
- If absent: adds `blocked-missing-security-analysis`, removes `backlog-candidate`, posts a comment
- If present after an edit: removes `blocked-missing-security-analysis` automatically

### Files

| File | Role |
|------|------|
| [.github/ISSUE_TEMPLATE/feature_request.yml](.github/ISSUE_TEMPLATE/feature_request.yml) | GitHub YAML issue form — all security fields required |
| [.github/workflows/issue-triage.yml](.github/workflows/issue-triage.yml) | Enforcement workflow — labels issues missing trust boundary selection |
| [REQUIREMENTS_TEMPLATE.md](REQUIREMENTS_TEMPLATE.md) | Extended AI-parseable requirements document for complex features |

---

## Phase 2 — Mandatory Planning Before Code

**Goal:** Anchor every Claude Code session to a GitHub issue and produce an approved implementation plan before any file is modified.

### How it works

1. The developer starts a Claude Code session. Because `defaultMode` is set to `"plan"` in `.claude/settings.json`, Claude starts in **read-only mode** — it can explore files but cannot edit them.
2. The developer runs `/plan <issue-number>`. Claude fetches the issue via the `gh` CLI, reads the security fields, explores the relevant source files, and outputs a structured plan.
3. The plan is reviewed by the developer. Claude does not write any code until the developer explicitly approves.
4. Only after approval does Claude exit read-only mode and begin making changes, guided by the rules in `CLAUDE.md`.

### Rules enforced by CLAUDE.md

- Every task must reference a GitHub issue — no coding without one
- `/plan <N>` must be run and approved before writing code
- Never implement features beyond the issue scope
- All user inputs validated server-side; reject invalid input outright (do not sanitize and continue)
- No raw SQL — Spring Data JPA parameterized queries only
- CSRF protection must remain enabled
- Role checks required on every new endpoint
- Commit messages must reference the issue: `feat: description (closes #N)`

### Output of `/plan <N>`

The plan command produces a document with six mandatory sections:

| Section | Content |
|---------|---------|
| **Scope** | What changes; what explicitly does NOT change (guards against feature creep) |
| **Affected Files** | Every file to be created or modified, with the method-level change |
| **Security Mapping** | Each change mapped to an SR-N requirement and an OWASP ASVS control |
| **Sequenced Tasks** | Ordered implementation steps with `file:method` references |
| **Edge Cases** | What can go wrong and how it is handled |
| **Human Review Checkpoints** | Milestones where Claude pauses for confirmation before continuing |

### Files

| File | Role |
|------|------|
| [CLAUDE.md](CLAUDE.md) | Project-level Claude Code rules — workflow, security, architecture, commit format |
| [.claude/settings.json](.claude/settings.json) | `defaultMode: "plan"` + pre-approved safe read commands + hook wiring |
| [.claude/commands/plan.md](.claude/commands/plan.md) | `/plan <N>` slash command — fetches issue, explores code, outputs structured plan |

---

## Phase 3 — Automated Security Review

**Goal:** Every changeset is reviewed for functional correctness and security before it reaches a commit.

### How it works

1. After writing code, the developer stages changes with `git add`.
2. The developer runs `/security-review`. Claude performs a two-pass review and outputs a verdict.
3. If the verdict is **APPROVED**, the developer runs `git commit`.
4. The `pre-commit-security.sh` hook is triggered automatically by the `PreToolUse` hook in `.claude/settings.json`. It runs Semgrep on the staged Java files.
5. If Semgrep finds any ERROR-severity findings, it exits with code 2 — Claude **blocks the commit** and feeds the findings summary back into context, directing the developer to fix the issues.
6. If Semgrep finds no critical findings, the commit proceeds.

### Two-pass review (`/security-review`)

**Pass 1 — Functional Correctness**
- Diffs staged changes against the linked issue's acceptance criteria
- Checks that all error paths are handled
- Verifies that tests exist for each acceptance criterion

**Pass 2 — Security**

| Category | What is checked |
|----------|----------------|
| A01 Broken Access Control | `@PreAuthorize` or `SecurityConfig` on every new endpoint; managers limited to subordinates |
| A02 Cryptographic Failures | BCrypt for passwords; no custom crypto; no plaintext storage |
| A03 Injection | JPA parameterized queries; no string-concatenated SQL; no SMTP header injection |
| A04 Insecure Design | Business rules validated in service layer, not only at controller |
| A05 Security Misconfiguration | CSRF never disabled; no wildcard `@CrossOrigin` |
| A07 Authentication Failures | No new public endpoints without explicit justification |
| A09 Logging Failures | No passwords or tokens in log statements; `GlobalExceptionHandler` does not echo `e.getMessage()` |
| Trust Boundaries | Every crossed boundary has a rejection-not-sanitize validation control |
| Defense Hierarchy | Architecture → Input Validation → Business Rules → Encoding → Sanitization (in that order) |
| Dependencies | New `pom.xml` entries flagged for human review |

### Blocking pre-commit hook

The hook script (`.claude/hooks/pre-commit-security.sh`) is wired to the `PreToolUse` event:
- Passes through immediately for any Bash command that is not `git commit`
- On `git commit`: extracts staged Java files, copies them to a temp directory, runs `semgrep scan --severity ERROR`
- **Exit code 2** (blocking) if any ERROR findings are found — Claude cannot proceed with the commit
- Outputs a findings summary to Claude's context and directs the developer to run `/security-review`

### Files

| File | Role |
|------|------|
| [.claude/commands/security-review.md](.claude/commands/security-review.md) | `/security-review` slash command — two-pass functional and security review |
| [.claude/hooks/pre-commit-security.sh](.claude/hooks/pre-commit-security.sh) | Shell hook — Semgrep on staged Java files; blocks commit on ERROR findings |
| [.claude/settings.json](.claude/settings.json) | Wires `PreToolUse` hook on Bash to the security script |

---

## Complete File Index

### New files added

```
.github/
├── ISSUE_TEMPLATE/
│   └── feature_request.yml       # GitHub YAML issue form with required security fields
└── workflows/
    └── issue-triage.yml           # Labels issues missing trust boundary selection

CLAUDE.md                          # Claude Code project rules (workflow + security)
REQUIREMENTS_TEMPLATE.md           # AI-parseable requirements document template

.claude/
├── settings.json                  # defaultMode: plan + PreToolUse hook config
├── commands/
│   ├── plan.md                    # /plan <N> — fetch issue → structured plan
│   └── security-review.md         # /security-review — two-pass security review
└── hooks/
    └── pre-commit-security.sh     # Semgrep gate; blocks git commit on ERROR findings
```

### Interaction diagram

```
Developer opens issue
  └─► feature_request.yml (template enforces security fields)
        └─► issue-triage.yml (blocks if no trust boundary checked)

Developer starts Claude session
  └─► .claude/settings.json → defaultMode: plan (read-only by default)
        └─► /plan <N>  (.claude/commands/plan.md)
              ├── reads issue via gh CLI
              ├── explores source files (read-only)
              └── outputs plan → waits for human approval
                    └─► CLAUDE.md rules guide implementation

Developer stages changes
  └─► /security-review  (.claude/commands/security-review.md)
        ├── Pass 1: functional correctness vs acceptance criteria
        └── Pass 2: OWASP Top 10 + trust boundaries + defense hierarchy
              └─► APPROVED → developer runs git commit

git commit
  └─► PreToolUse hook → pre-commit-security.sh
        ├── semgrep scan on staged Java files
        ├── ERROR found → exit 2 → commit BLOCKED
        └── clean → commit proceeds
```

---

## Verification Checklist

| Check | How to verify |
|-------|--------------|
| Issue template fields | Open a new issue in GitHub UI — all fields should appear with required markers |
| Triage workflow | Submit an issue without checking any trust boundary — expect `blocked-missing-security-analysis` label and comment within ~30 seconds |
| `defaultMode: plan` | Start a new Claude Code session — Claude should be in read-only mode by default |
| `/plan` command | Run `/plan <issue-number>` — Claude should output all six plan sections and stop before writing code |
| `/security-review` | Stage a file with a known-bad pattern (e.g., string-concat SQL) — expect BLOCKED verdict with A03 finding |
| Pre-commit hook | With the same bad file staged, attempt `git commit` — Claude should be blocked by the hook with a Semgrep summary |
| Clean commit | Fix the finding, re-run `/security-review` (APPROVED), then `git commit` — should proceed without blocking |

---

## Notes and Limitations

- **Trust boundaries checkboxes** in GitHub Forms cannot be set as natively `required` by GitHub — enforcement is handled by `issue-triage.yml` instead.
- **`defaultMode: "plan"`** applies to all Claude Code sessions in this project. To make changes, the developer (or Claude after plan approval) must exit plan mode explicitly.
- **The pre-commit hook** only intercepts commits made through Claude Code's Bash tool. To enforce the same gate on direct terminal commits, add a separate `laps/.git/hooks/pre-commit` script (not tracked by git).
- **`/plan` and `/security-review`** require the `gh` CLI to be authenticated. Run `gh auth status` to verify before use.
- **Semgrep** must be installed and on `PATH` for the pre-commit hook to run. It is installed automatically in CI (`ci.yml`) but must be installed locally for developer workstations (`pip install semgrep` or `brew install semgrep`).
