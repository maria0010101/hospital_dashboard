# AGENTS.md — Hospital Report Platform Development Rules

> **Purpose**
>
> This file defines the mandatory behavior rules for any AI coding agent (Claude Code, Cursor Agent, Codex, or equivalent) operating inside this repository.
>
> **This file is not the complete product specification.**
> - `AGENTS.md` = Agent behavior and development rules
> - `SRS.md` = Complete Software Requirements Specification
> - `REPORT_ARCHITECTURE.md` = Repository architecture inventory produced/maintained by the Agent after inspection
> - `REPORT_MIGRATION.md` = Report-by-report migration status and evidence
>
> When these documents conflict, do not silently choose one. Stop and report the conflict to the user.

---

## 1. Mission

The long-term goal is to evolve the existing hospital reporting system from scattered, report-specific implementations into a **configuration-driven, unified reporting platform**.

The target conceptual model is:

```text
Metric
  + Dimension
  + Filter
  + Aggregation
  + Renderer
        ↓
Configurable Report
        ↓
Hospital-wide / Campus / Department / Campus + Department
```

The Agent must optimize for:

1. **Business definition correctness**
2. **Consistency of dimensions**
3. **Reuse of reporting logic**
4. **Configuration-driven behavior**
5. **Safe incremental migration**
6. **Regression safety**
7. **Maintainability**
8. **Traceability**

Do not optimize for code reduction at the expense of business correctness.

---

# 2. Mandatory Startup Procedure

Every time the Agent enters this repository, it MUST perform the following procedure before making substantive code changes.

## Step 1 — Read the governing documents

Read, if present:

```text
AGENTS.md
SRS.md
REPORT_ARCHITECTURE.md
REPORT_MIGRATION.md
```

Also inspect relevant project documentation such as:

```text
README*
docs/
CONTRIBUTING*
```

If `SRS.md` is missing:

- do not invent the requirements;
- report that the SRS is missing;
- use the current task only if the user explicitly authorizes proceeding without it.

If `REPORT_ARCHITECTURE.md` is missing:

- create/update an architecture inventory before performing a large refactor;
- for a small isolated task, inspect only the minimum necessary code and clearly state that the architecture inventory is incomplete.

If `REPORT_MIGRATION.md` is missing:

- create it before beginning a report migration.

---

## Step 2 — Inspect the repository before modifying it

Before changing reporting architecture, identify:

- application entry points;
- report modules;
- report controllers/routes;
- report services;
- SQL/query definitions;
- stored procedures;
- ORM/repository/data-access code;
- report templates/renderers;
- export functions;
- configuration files;
- dimension definitions;
- campus/department mappings;
- duplicated report logic;
- existing aggregation logic;
- tests;
- fixtures/sample data;
- legacy compatibility code.

Do not assume the repository structure.

Use repository search tools aggressively.

---

## Step 3 — Establish the current architecture

Before a broad change, update:

```text
REPORT_ARCHITECTURE.md
```

The inventory should identify, where applicable:

```text
Report
 ├─ Entry point
 ├─ Data source
 ├─ Query
 ├─ Business rules
 ├─ Dimensions
 ├─ Filters
 ├─ Aggregations
 ├─ Renderer
 ├─ Export
 ├─ Tests
 └─ Known duplication
```

Every important finding should include a repository path and, where useful, a symbol/class/function name.

---

# 3. Never Guess Hospital Business Rules

This is a **critical rule**.

The Agent MUST NOT invent or silently reinterpret:

- medical definitions;
- NHI/NHIA reporting rules;
- case classification;
- campus classification;
- department classification;
- physician attribution;
- denominator definitions;
- numerator definitions;
- inclusion/exclusion criteria;
- counting units;
- deduplication rules;
- statistical periods;
- reimbursement/business rules;
- legacy report meanings.

If a business rule is unclear:

```text
STOP → identify ambiguity → report evidence → ask user
```

Do not "clean up" an apparently strange rule merely because a more elegant implementation seems possible.

---

# 4. Preserve Existing Business Semantics During Refactoring

Refactoring must distinguish between:

### A. Structural duplication

Safe candidate for consolidation:

- repeated SQL fragments;
- repeated dimension filters;
- duplicated aggregation functions;
- repeated report configuration;
- duplicated rendering code;
- repeated validation.

### B. Business-rule differences

Do NOT consolidate merely because two reports look similar.

Examples:

```text
COUNT(*)
COUNT(DISTINCT patient_id)
COUNT(DISTINCT encounter_id)
SUM(amount)
SUM(points)
```

are not interchangeable.

Likewise:

```text
department = clinical_department
```

must not automatically become:

```text
department = billing_department
```

The Agent must preserve semantics first and abstract second.

---

# 5. Unified Dimension Rules

The reporting platform MUST treat dimensions as first-class concepts.

Initial standard dimensions include:

```text
TIME
CAMPUS
DEPARTMENT
ORGANIZATION
```

Future dimensions may include:

```text
DOCTOR
WARD
TEAM
CASE_TYPE
VISIT_TYPE
INSURANCE_TYPE
DIAGNOSIS
PROCEDURE
```

Do not create report-specific representations of the same logical dimension unless there is a documented business reason.

For example, avoid:

```text
campus_id
hospital_area
branch_code
site
院區
院區別
```

being independently interpreted by every report.

Instead, establish a canonical dimension model and explicit mappings.

---

# 6. Dimension Hierarchy

Where supported by the business model, the Agent should preserve a hierarchy such as:

```text
Organization
    ↓
Campus
    ↓
Department
    ↓
Team / Ward / Unit
    ↓
Doctor
```

Do not assume every repository has exactly this hierarchy.

The actual hierarchy must be documented in:

```text
REPORT_ARCHITECTURE.md
```

The Agent must not create a hierarchy that is not supported by existing data/business rules.

---

# 7. Configuration-Driven Reporting

The desired architecture is configuration-driven.

Prefer:

```text
Report Definition
    ↓
Report Context
    ↓
Dimension Resolution
    ↓
Data Query
    ↓
Metric Calculation
    ↓
Aggregation
    ↓
Renderer
```

over:

```text
if campus:
    ...
elif department:
    ...
elif campus and department:
    ...
```

repeated independently inside every report.

However, do NOT introduce an abstraction merely because it looks architecturally elegant.

A new abstraction must demonstrate reuse or prevent a real class of duplication.

---

# 8. Report Context

Where the architecture supports it, report execution should have an explicit context concept.

Conceptually:

```text
ReportContext
├── period
├── organization
├── campus
├── department
├── dimensions
├── filters
└── options
```

The Agent should avoid passing the same collection of parameters independently through many layers.

Prefer:

```text
generateReport(context)
```

over large parameter lists such as:

```text
generateReport(
    startDate,
    endDate,
    campus,
    department,
    organization,
    reportType,
    ...
)
```

unless the existing framework strongly favors another pattern.

---

# 9. Metrics Must Be Explicit

Metrics should have identifiable definitions.

Conceptually:

```text
Metric
├── id
├── name
├── value
├── unit
├── aggregation
└── definition
```

A metric should not become ambiguous simply because the UI label is the same.

For example:

```text
Patient Count
Encounter Count
Procedure Count
Application Points
Revenue
Average Length of Stay
```

must retain their distinct counting/aggregation semantics.

---

# 10. Aggregation Rules

Aggregation must be explicit.

Common examples:

```text
SUM
COUNT
COUNT_DISTINCT
AVG
MIN
MAX
RATIO
PERCENTAGE
RATE
```

Never replace a distinct count with a normal count without evidence.

Be particularly careful with:

- patient-level metrics;
- encounter-level metrics;
- case-level metrics;
- order-level metrics;
- item-level metrics;
- duplicated joins.

Before changing aggregation logic, identify the intended grain:

```text
Patient
Encounter
Case
Order
Item
Transaction
```

Document the grain when it is material to correctness.

---

# 11. Hospital-Wide / Campus / Department Conversion

A core requirement is that the same report definition should, where business rules permit, support:

```text
Hospital-wide
Campus
Department
Campus + Department
```

The Agent must prefer:

```text
same metric definition
same report definition
different ReportContext
```

rather than maintaining separate implementations such as:

```text
HospitalReport
CampusReport
DepartmentReport
CampusDepartmentReport
```

If separate implementations are genuinely necessary, document why in:

```text
REPORT_ARCHITECTURE.md
```

---

# 12. Do Not Introduce Hard-Coded Report Branches

Avoid patterns such as:

```text
if report == A:
    ...
elif report == B:
    ...
elif report == C:
    ...
```

when the behavior can be expressed through report metadata/configuration.

Prefer a registry/configuration approach such as:

```text
ReportRegistry
ReportConfiguration
DimensionRegistry
MetricRegistry
```

Names are illustrative, not mandatory.

The Agent should adapt the pattern to the existing project's language and architecture.

---

# 13. Report Registry

If a registry is introduced, it should provide a discoverable mapping such as:

```text
report_id
display_name
dataset
dimensions
metrics
filters
renderer
permissions
```

Avoid scattering report metadata across unrelated files.

Do not create a registry solely for theoretical purity if the project has an existing equivalent mechanism.

---

# 14. Migration Strategy

The Agent MUST use **incremental migration**.

Never rewrite every report at once.

Preferred sequence:

```text
Inventory
  ↓
Architecture
  ↓
Shared abstraction
  ↓
One representative report
  ↓
Legacy/new comparison
  ↓
Regression tests
  ↓
Production validation
  ↓
Migrate next report
```

---

# 15. Proof-of-Concept Rule

The first migrated report should be representative.

Prefer a report that has:

- multiple dimensions;
- meaningful aggregation;
- campus/department filtering;
- existing duplication;
- measurable output;
- available historical/fixture data;
- testable expected results.

Do not choose the most complicated report simply because it is important.

Do not choose an artificially trivial report that cannot validate the architecture.

---

# 16. Legacy vs New Comparison

Every migrated report must have evidence that the new implementation preserves the old business result.

Where possible compare:

```text
Same period
Same organization
Same campus
Same department
Same filters
Same source data
```

Compare at minimum:

```text
row count
metric values
totals
subtotals
dimension labels
null handling
zero handling
sorting
filter behavior
```

Differences must be classified:

```text
Expected business correction
Expected technical difference
Regression
Unknown
```

Unknown differences must not be silently accepted.

---

# 17. REPORT_MIGRATION.md Is Mandatory for Migration Work

Maintain a table similar to:

```text
| Report | Status | Architecture | Config | Dimensions | Regression | Production |
|--------|--------|--------------|--------|------------|------------|------------|
| R001   | Done   | Yes          | Yes    | Yes        | Pass       | Pending    |
| R002   | PoC    | Yes          | Yes    | Partial    | Running    | No         |
```

Allowed status values:

```text
DISCOVERED
DESIGNED
POC
IN_MIGRATION
REGRESSION
VALIDATED
DONE
BLOCKED
DEFERRED
```

Each migrated report should include:

- source paths;
- target paths;
- business rule notes;
- dimensions;
- metrics;
- migration date;
- test evidence;
- known limitations.

---

# 18. Testing Requirements

A refactor is not complete merely because the application builds.

Tests should cover, where applicable:

### Dimension

```text
Hospital-wide
Campus A
Campus B
Department X
Campus A + Department X
```

### Time

```text
single month
multiple months
quarter
year
boundary dates
```

### Filters

```text
no filter
single filter
multiple filters
empty result
```

### Aggregation

```text
SUM
COUNT
COUNT_DISTINCT
AVG
ratio/rate
```

### Edge cases

```text
NULL
0
duplicate records
missing mapping
unknown dimension
empty dataset
```

---

# 19. Data Grain Must Be Known Before Aggregation Changes

Before modifying SQL or data transformations, answer:

```text
What is one row of the source dataset?
```

Possible answers:

```text
one patient
one encounter
one case
one order
one medical item
one claim
one transaction
```

If the grain is unknown, do not safely generalize aggregation.

---

# 20. SQL and Data Access Rules

When modifying SQL:

1. preserve parameterization;
2. avoid SQL injection;
3. avoid unnecessary full-table scans;
4. inspect join cardinality;
5. verify duplicate multiplication;
6. preserve indexes where relevant;
7. avoid `SELECT *` in new shared datasets;
8. do not change business filters without evidence;
9. document important query assumptions.

For hospital/NHI data, do not put patient-identifiable information into logs, exceptions, test output, screenshots, or generated documentation.

Use synthetic identifiers or masked examples.

---

# 21. Security and Privacy

The Agent must assume reporting data may contain sensitive medical information.

Never:

- commit real patient data;
- place real identifiers in fixtures;
- log patient IDs;
- paste production data into documentation;
- create debug endpoints exposing raw records;
- weaken authentication/authorization merely to simplify testing.

Use:

```text
synthetic data
masked data
minimal fixtures
```

---

# 22. Performance Rules

The new architecture must not introduce unnecessary performance regressions.

Before and after migration, consider:

```text
query execution time
number of database calls
rows transferred
memory usage
rendering time
export time
```

Avoid:

```text
N+1 queries
repeated identical queries
unnecessary materialization
large in-memory grouping when DB aggregation is appropriate
```

Do not optimize prematurely.

Measure meaningful bottlenecks.

---

# 23. Backward Compatibility

Unless explicitly authorized, do not break:

- existing report URLs;
- API contracts;
- exported file formats;
- report names;
- existing configuration;
- permissions;
- downstream integrations.

If a breaking change is required:

1. explain why;
2. identify affected consumers;
3. propose migration;
4. obtain user approval before implementing the breaking change.

---

# 24. UI Rules

The UI should expose dimensions consistently.

For example:

```text
分析期間
組織
院區
科別
其他篩選條件
```

Do not create different terminology or filter behavior for equivalent dimensions in different reports.

The Agent should separate:

```text
business/report definition
```

from:

```text
presentation/rendering
```

A report's calculation must not depend unnecessarily on whether it is displayed as:

```text
table
chart
dashboard
Excel
CSV
PDF
API
```

---

# 25. Renderer Separation

Prefer:

```text
Data / Metrics
      ↓
Report Result
      ↓
Renderer
```

over embedding business logic inside:

```text
HTML template
Excel generation
chart rendering
PDF rendering
```

Renderers should consume already-defined report results.

---

# 26. File and Module Naming

Use names that communicate responsibility.

Prefer concepts such as:

```text
report/
    context/
    dimensions/
    metrics/
    aggregation/
    configuration/
    registry/
    datasets/
    renderers/
```

But do not mechanically create these folders.

Follow the existing project's conventions when they are sound.

---

# 27. Avoid Premature Abstraction

Before creating a shared abstraction, answer:

1. What duplication does it remove?
2. Which reports will use it?
3. What business rule does it standardize?
4. How will it be tested?
5. Can it be safely extended?

If the answer is unclear, do not introduce the abstraction yet.

---

# 28. Change Scope Control

For every task, define:

```text
In Scope
Out of Scope
```

Do not silently expand a task.

If a discovered architectural problem is unrelated:

```text
record it
do not fix it automatically
```

unless it blocks the current task or the user explicitly requests broader cleanup.

---

# 29. No Mass Deletion

Never mass-delete:

- old reports;
- SQL;
- configuration;
- APIs;
- templates;
- stored procedures;
- compatibility code.

Old code may contain undocumented business rules.

Delete legacy implementation only after:

```text
new implementation validated
regression passed
dependencies checked
migration status updated
user/project policy permits removal
```

---

# 30. Documentation Is Part of the Implementation

For architecture-changing work, documentation updates are part of Definition of Done.

At minimum, update relevant:

```text
REPORT_ARCHITECTURE.md
REPORT_MIGRATION.md
```

For significant design decisions, also consider:

```text
REPORT_DESIGN.md
REPORT_DIMENSIONS.md
REPORT_TEST_MATRIX.md
REPORT_CHANGELOG.md
```

Do not create documentation filled with generic architecture theory. Documentation should describe the actual repository.

---

# 31. Agent Working Modes

The Agent should explicitly identify its current mode.

## MODE A — DISCOVERY

Allowed:

- read code;
- search;
- inspect SQL;
- inspect configuration;
- map dependencies;
- document architecture.

Not allowed:

- broad production-code refactoring;
- deleting legacy code;
- changing business rules.

Deliverables:

```text
REPORT_ARCHITECTURE.md
```

and, when useful:

```text
REPORT_DESIGN.md
REPORT_DIMENSIONS.md
```

---

## MODE B — DESIGN

Allowed:

- propose target architecture;
- define interfaces;
- define configuration schemas;
- define migration strategy;
- identify PoC report.

Do not implement broad changes until the design is sufficiently understood.

---

## MODE C — POC / IMPLEMENTATION

Implement one representative report.

Required:

```text
shared abstraction
configuration
dimension handling
metric handling
aggregation
renderer separation
tests
legacy/new comparison
```

Update:

```text
REPORT_MIGRATION.md
```

---

## MODE D — MIGRATION

Migrate reports incrementally.

For each report:

```text
inspect
design mapping
implement
test
compare
validate
document
```

Never assume a report is migrated because it compiles.

---

## MODE E — VALIDATION

Focus on:

```text
business correctness
regression
performance
permissions
data privacy
UI behavior
exports
```

Do not introduce unrelated refactoring in validation mode.

---

# 32. Required Agent Output Before Large Changes

Before a large architectural modification, the Agent should provide a concise plan containing:

```text
1. Current architecture
2. Problem identified
3. Proposed change
4. Files/modules affected
5. Business rules preserved
6. Tests to add/update
7. Migration impact
8. Risks
9. Rollback strategy
```

Then proceed only when the task's authorization level permits it.

---

# 33. Required Agent Output After Changes

After implementation, report:

```text
## Changes
- ...

## Files Changed
- ...

## Business Rules Preserved
- ...

## Architecture Impact
- ...

## Tests
- ...

## Legacy/New Comparison
- ...

## Migration Status
- ...

## Known Limitations
- ...

## Remaining Risks
- ...
```

Do not claim a test passed unless it was actually executed.

Do not claim production validation unless it actually occurred.

---

# 34. Git / Commit Discipline

Prefer small, logically isolated commits.

Suggested boundaries:

```text
1. docs: add reporting architecture inventory
2. refactor: introduce report context
3. refactor: introduce unified dimensions
4. feat: migrate PoC report
5. test: add legacy/new regression coverage
6. docs: update migration status
```

Do not combine unrelated cleanup with a report migration.

Do not rewrite Git history unless explicitly instructed.

---

# 35. Stop Conditions

The Agent MUST stop and ask the user when:

- a business definition is ambiguous;
- two reports use conflicting definitions of the same metric;
- campus/department mapping is unclear;
- aggregation grain is unclear;
- a migration would change existing results unexpectedly;
- a breaking API change is required;
- production data is required but unavailable;
- permissions/security would need to be weakened;
- a migration would require deleting legacy logic before validation;
- the requested change conflicts with the SRS;
- the repository architecture differs materially from assumptions in the SRS.

When stopping, provide:

```text
Problem
Evidence
Possible options
Recommended option
Question requiring user decision
```

---

# 36. Definition of Done

A report migration is **DONE** only when all applicable conditions are satisfied:

```text
[ ] Business definition documented
[ ] Source data identified
[ ] Data grain identified
[ ] Dimensions identified
[ ] Metrics identified
[ ] Filters identified
[ ] Aggregation rules identified
[ ] Configuration implemented
[ ] Shared logic reused where appropriate
[ ] Renderer separated from business logic
[ ] Hospital-wide mode verified
[ ] Campus mode verified
[ ] Department mode verified
[ ] Campus + Department mode verified
[ ] Edge cases tested
[ ] Legacy/new comparison completed
[ ] Regression tests pass
[ ] Security/privacy checked
[ ] Performance checked when material
[ ] REPORT_MIGRATION.md updated
[ ] REPORT_ARCHITECTURE.md updated when architecture changed
[ ] No unexplained result differences remain
```

---

# 37. Priority Rules

When rules conflict, use this priority:

```text
1. Explicit user instruction
2. Business correctness / hospital reporting definition
3. SRS.md
4. AGENTS.md
5. Existing validated behavior
6. REPORT_ARCHITECTURE.md
7. REPORT_MIGRATION.md
8. General software design preference
9. Agent convenience
```

However, if an explicit user instruction would cause a safety/security/privacy problem, the Agent must flag it before proceeding.

---

# 38. Core Design Principle

Always keep the following principle in mind:

```text
Do not build four reports when you need one report
with four analysis contexts.
```

The preferred evolution is:

```text
                 ┌──────────────┐
                 │ Report Config │
                 └──────┬───────┘
                        ↓
                 ┌──────────────┐
                 │ ReportContext│
                 └──────┬───────┘
                        ↓
          ┌─────────────┼─────────────┐
          ↓             ↓             ↓
     Dimensions      Metrics      Filters
          │             │             │
          └─────────────┼─────────────┘
                        ↓
                 ┌──────────────┐
                 │ Aggregation  │
                 └──────┬───────┘
                        ↓
                 ┌──────────────┐
                 │ Report Result│
                 └──────┬───────┘
                        ↓
                  ┌─────┴─────┐
                  ↓           ↓
                Table       Chart
                  ↓           ↓
                Excel        API
```

The Agent's job is not merely to "refactor code".

The Agent's job is to **make the reporting system progressively more consistent, configurable, testable, and maintainable without changing the meaning of hospital operational data**.

---

# 39. First Task When Starting This Refactoring Project

If the repository has not yet been architecturally inventoried, the first task is:

```text
DO NOT immediately rewrite reports.

1. Read SRS.md
2. Inspect repository
3. Inventory every report
4. Identify common dimensions
5. Identify common metrics
6. Identify aggregation grains
7. Identify duplicated logic
8. Identify current configuration mechanisms
9. Identify candidate PoC report
10. Create/update REPORT_ARCHITECTURE.md
11. Create/update REPORT_MIGRATION.md
12. Present findings and recommended PoC
```

Only after this discovery stage should broad implementation begin.

---

# 40. Repository Governance Files

The intended repository governance structure is:

```text
/
├── AGENTS.md
├── SRS.md
├── REPORT_ARCHITECTURE.md
├── REPORT_MIGRATION.md
├── REPORT_DESIGN.md
├── REPORT_DIMENSIONS.md
├── REPORT_TEST_MATRIX.md
└── REPORT_CHANGELOG.md
```

Not every optional document must exist immediately.

The minimum required governance files are:

```text
AGENTS.md
SRS.md
REPORT_ARCHITECTURE.md
REPORT_MIGRATION.md
```

### Responsibility of each file

| File | Responsibility |
|---|---|
| `AGENTS.md` | How the AI Agent must behave |
| `SRS.md` | What the system must achieve |
| `REPORT_ARCHITECTURE.md` | What the repository actually contains |
| `REPORT_MIGRATION.md` | Which reports have been migrated and validated |
| `REPORT_DESIGN.md` | Detailed target architecture/design decisions |
| `REPORT_DIMENSIONS.md` | Canonical dimension definitions/mappings |
| `REPORT_TEST_MATRIX.md` | Regression and validation matrix |
| `REPORT_CHANGELOG.md` | Architecture/report migration history |

---

## Final Rule

**Read first. Inspect second. Design third. Change fourth. Validate fifth. Document always.**

Never sacrifice hospital reporting correctness for architectural elegance.
