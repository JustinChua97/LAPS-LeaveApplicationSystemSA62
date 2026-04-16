# Secure AI-Assisted Development Workflow

This repository uses a three-phase governance workflow for AI-assisted changes. The
requirements and security gates are shared across Claude Code and Codex; only the local
agent integration differs.

## Overview

```
PHASE 1: Structured Requirements
GitHub Issue Template -> issue-triage workflow -> backlog eligibility

PHASE 2: Planning Before Code
Issue requirements -> agent plan -> human approval -> implementation

PHASE 3: Automated Security Review
Functional pass -> security pass -> Semgrep pre-commit gate -> commit
```

All three phases are required. No code should be committed unless the issue is complete,
the plan is approved, and the changeset has passed security review.

## Phase 1 - Structured Requirements

**Goal:** force threat modeling and validation requirements before work enters the backlog.

Developers create feature work through `.github/ISSUE_TEMPLATE/feature_request.yml`.
The template requires machine-readable fields:

| Field | Purpose |
|-------|---------|
| Summary | User story and reason for the change |
| Acceptance Criteria | Binary pass/fail behavior contract |
| Security Considerations | ASVS control mapping and security requirements |
| Threat Surface | New or changed endpoints, roles, data stores, templates, APIs, files, external services, and abuse cases |
| Trust Boundaries Affected | Checked boundaries crossed by the change |
| Input / Output Contracts | Field names, types, lengths, outputs, and server-side validation rules per boundary |
| Testability Criteria | Unit, integration, and security negative tests |
| Data Flow | Text diagram through controller, service, repository, database, and external systems |

`.github/workflows/issue-triage.yml` runs when issues are opened or edited. It removes
`backlog-candidate`, adds `blocked-missing-security-analysis`, and comments on the issue
unless all of these are present:

- at least one checked trust boundary
- a non-empty Threat Surface section
- explicit server-side validation requirements in Input / Output Contracts

`REQUIREMENTS_TEMPLATE.md` provides the expanded AI-parseable version for complex
features. Its `SECTION:` markers are stable and should be treated as the parse contract
for implementation agents.

## Phase 2 - Mandatory Planning Before Code

**Goal:** anchor implementation to a reviewed GitHub issue and prevent feature invention.

### Claude Code Path

- `CLAUDE.md` contains the Claude project rules.
- `.claude/settings.json` starts Claude in plan mode and wires Claude `PreToolUse` hooks.
- `.claude/commands/plan.md` is the `/plan <issue-number>` slash command.

### Codex Path

- `AGENTS.md` contains the Codex project rules.
- `.codex/prompts/plan.md` is the Codex planning prompt template.
- Codex must fetch `gh issue view <N>`, parse the required issue fields, inspect the repo read-only, output a single `<proposed_plan>` block, and stop before editing until the user approves.

Both agents must produce a plan covering:

- scope and explicit out-of-scope items
- affected components and method-level changes
- architecture decisions
- security controls and ASVS/OWASP mapping
- sequenced implementation tasks
- edge cases and failure modes
- test strategy
- rollback approach
- human review checkpoints

## Phase 3 - Automated Security Review

**Goal:** verify functional correctness and security before changes reach a commit.

### Two-Pass Review

Use `.claude/commands/security-review.md` for Claude or
`.codex/prompts/security-review.md` for Codex.

Pass 1 checks functional correctness:

- staged diff matches every acceptance criterion
- no undocumented feature creep
- error paths are explicit
- tests cover the issue contract

Pass 2 checks security:

| Category | What is checked |
|----------|-----------------|
| A01 Broken Access Control | New endpoints have role checks; manager actions verify direct subordinate ownership |
| A02 Cryptographic Failures | BCrypt for passwords; no custom crypto or plaintext secrets |
| A03 Injection | JPA parameterized queries; no SQL/JPQL string concatenation; no SMTP header injection |
| A04 Insecure Design | Business rules enforced in the service layer |
| A05 Security Misconfiguration | CSRF remains enabled; no unjustified wildcard CORS |
| A07 Authentication Failures | No undocumented anonymous endpoints |
| A09 Logging Failures | No secrets in logs; no raw stack traces or exception messages to users |
| Trust Boundaries | Every crossed boundary has authorization, validation, and reject-not-sanitize behavior |
| Defense Hierarchy | Architecture, validation, strict APIs, encoding, then sanitization only as last resort |
| Dependencies | New dependencies are justified and checked for known CVEs |

Any CRITICAL or HIGH finding blocks commit until fixed and reviewed again.

## Commit Blocking

Claude Code has a Claude-specific hook at `.claude/hooks/pre-commit-security.sh`, wired
through `.claude/settings.json`.

Codex uses a normal Git hook because Claude `PreToolUse` hooks are not portable:

```bash
bash scripts/install-codex-hooks.sh
```

The installer symlinks `.git/hooks/pre-commit` to
`.codex/hooks/pre-commit-security.sh`. The hook scans staged Java files with Semgrep
configs `p/java`, `p/owasp-top-ten`, and `p/secrets`. It fails closed if Semgrep is not
available and blocks commits with ERROR-severity findings.

Do not bypass the gate with `--no-verify`.

## File Index

Shared workflow files:

| File | Role |
|------|------|
| `.github/ISSUE_TEMPLATE/feature_request.yml` | Required GitHub issue form |
| `.github/workflows/issue-triage.yml` | Backlog eligibility gate for security fields |
| `REQUIREMENTS_TEMPLATE.md` | AI-parseable expanded requirements template |
| `WORKFLOW.md` | This workflow guide |

Claude-specific files:

| File | Role |
|------|------|
| `CLAUDE.md` | Claude project rules |
| `.claude/settings.json` | Claude plan mode and hook configuration |
| `.claude/commands/plan.md` | Claude planning slash command |
| `.claude/commands/security-review.md` | Claude two-pass review slash command |
| `.claude/hooks/pre-commit-security.sh` | Claude `PreToolUse` Semgrep gate |

Codex-specific files:

| File | Role |
|------|------|
| `AGENTS.md` | Codex project rules |
| `.codex/prompts/plan.md` | Codex planning prompt |
| `.codex/prompts/security-review.md` | Codex two-pass review prompt |
| `.codex/hooks/pre-commit-security.sh` | Git pre-commit Semgrep gate source |
| `scripts/install-codex-hooks.sh` | Installs the Codex Git pre-commit hook |

## Verification Checklist

| Check | How to verify |
|-------|---------------|
| Issue template | Open a new GitHub feature issue and confirm all required fields render |
| Triage workflow | Test sample issue bodies missing trust boundary, threat surface, or validation requirements |
| Claude plan command | Run `/plan <issue-number>` and confirm Claude stops after plan output |
| Codex plan prompt | Ask Codex to use `.codex/prompts/plan.md` and confirm it emits one `<proposed_plan>` block |
| Security review prompt | Stage changes and run the relevant two-pass review prompt |
| Codex hook syntax | Run `bash -n .codex/hooks/pre-commit-security.sh` |
| Installer syntax | Run `bash -n scripts/install-codex-hooks.sh` |
| Codex hook install | Run `bash scripts/install-codex-hooks.sh` and inspect `.git/hooks/pre-commit` |
| Commit gate | Stage a known-bad Java pattern and confirm the hook blocks the commit |

## Notes and Limitations

- GitHub issue form checkbox groups cannot be made natively required, so
  `issue-triage.yml` enforces trust-boundary selection after issue submission.
- `.codex/prompts/*.md` are prompt templates, not native slash commands.
- The Codex hook only runs after installation because Git does not track `.git/hooks`.
- Semgrep must be installed locally for the hook to pass. CI also runs Semgrep through
  the existing workflow.
- The security review prompt may flag dependency CVEs for human review when local tools
  cannot query vulnerability databases.
