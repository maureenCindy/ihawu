# Contributing to Ihawu

Thank you for your interest in contributing to Ihawu! We are excited to build a zero-boilerplate, framework-agnostic
data privacy layer for the JVM ecosystem alongside the open-source community.

To maintain an elite developer experience (DX), absolute security reliability, and clean code hygiene, we follow a 
structured contribution model inspired by the JetBrains standard library repository layout.

---

## Code of Conduct & Architecture Rules

Before you open a terminal or write any code, you must commit to our three golden architectural constraints:

1. **Strict JVM Backend Focus:** Ihawu is built using **pure Kotlin (JVM)**. Do not introduce Multiplatform (KMP) 
compile configurations or native cross-platform artifacts. We target **Java 17+** optimization profiles.
2. **Absolute Core Isolation:** The `ihawu-core` module must remain entirely unpolluted by web frameworks. 
You are strictly forbidden from adding imports from `org.springframework.*`, `io.ktor.*`, or heavy Java servlet layers
inside `ihawu-core`. If you need the caller's identity, use the core `IhawuPrincipal` abstraction. Jackson *is*
currently permitted in core — it is the serialization engine masking is built on. Removing that coupling is tracked
under the *0.3.0 — Serialization-neutral core* milestone.
3. **No Heavy Runtime Reflection:** Dynamic field masking evaluates on hot response pathways right before JSON output 
serialization. Do not introduce slow runtime reflection (`java.lang.reflect`) inside processing pipelines. 
Leverage cached property maps or framework native streaming filters.

---

## The Executable Documentation Mandate

We do not accept static, unchecked code snippets in our public documentation. 
Every single code example visible in our API references or Markdown guides must be verified by the compiler.

* If you modify a public interface, add a feature, or resolve a tracking issue under our text-specialization milestones,
you **must create or update a corresponding compiling function** inside the `samples` directory.
* Your source code file must use the Dokka **`@sample`** tag to target that specific function.
* The continuous integration (CI) workflow executes `./gradlew dokkaGenerate` on every pull request. 
If your documentation sample fails compilation, the build fails fast, protecting our documentation from breaking.

---

## Local Workspace Setup

Getting your local environment ready takes less than two minutes:

1. **Prerequisites:** Ensure you have **JDK 17** (or later) installed locally.
2. **Clone the Project**
3. **Run the Verification Suite:** Validate the initial "Walking Skeleton" build architecture locally before writing
changes:
   ```bash
   ./gradlew check
   ```
   
---

## Lifecycle of a Contribution

We manage our open-source roadmap directly through our **GitHub Project Board**. We do not use external issue trackers.
1. Choose Issue / File Bug 
2. Open PR with Checklist 
3. CI Lint & Test Gate 
4. Maintainer Merge 

### 1. Claiming an Issue
* Navigate to our GitHub Issues page. Look for tickets flagged with the **`good first issue`** label.
* Comment on the issue to request assignment. This avoids duplicate effort from other community contributors.

### 2. Branch Hygiene
Create a descriptive branch branching off of our `main`/`master` target:
```bash
git checkout -b feature/issue-number-short-description
```

### 3. Commit Hygiene & Conventional Commits

To maintain a clean, readable, and trackable project history, Ihawu strictly enforces the **Conventional Commits**
specification. Our automated release pipeline maps these commit prefixes directly to version bump requirements 
(Semantic Versioning) and auto-generates our release changelogs.

#### i. Commit Message Structure
Every commit message must follow this exact format - subject + body:
```text
type(scope): clear and concise description in present tense

Detailed body explaining the 'why' behind the change if necessary.
```

#### ii. Allowed Commit Types
*   `feat`: A new user-facing feature (e.g., `feat(spring): add response masking advice handler`)
*   `fix`: A bug fix (e.g., `fix(ktor): resolve serialization error on non-nullable strings`)
*   `docs`: Documentation-only updates or sample updates (e.g., `docs(samples): add executable code for redact strategy`)
*   `style`: Code style modifications only (formatting, missing semi-colons, ktlint fixes)
*   `refactor`: Code changes that neither fix a bug nor add a feature
*   `test`: Adding missing tests or correcting existing test suites
*   `chore`: Updating build scripts, dependencies, or pipeline configurations

#### iii. Git Commit Rules
*   **Atomic Commits:** Keep your commits focused. Do not mix a feature implementation with a dependency update or an 
unrelated typo fix in the same commit.
*   **Present Tense:** Write messages in the imperative present tense. Write `feat(core): add field masker contract`, 
not `feat(core): added field masker contract`.
*   **No Cryptic Messages:** Commits like `fix`, `updates`, or `working now` will fail our automated semantic validation
checks and cause the pull request review to be blocked.
*   **Squashing before Merge:** If your pull request contains iterative "fix typo" or "wip" commits, you will be asked 
to squash your branch history into clean, atomic semantic blocks before a maintainer hits merge.


### 4. Submission Checklist
When you open a Pull Request (PR), our automated template will ask you to confirm the following points:
* [ ] Does your code follow the standard Kotlin code style configurations? (Verify by running `./gradlew ktlintCheck`).
* [ ] Did you include a compiling execution test inside the `samples` module?
* [ ] Does your pull request description contain a closing keyword linked to an active issue? (e.g., `Closes #14`).

### 5. Automated CI Gating
Once your PR is submitted, GitHub Actions will fire two parallel pipelines:
* **`verify.yml`**: Runs code compilers, checkstyle rules, and unit tests across all framework integration packages.
* **Dokka Pages Worker**: Generates a temporary site preview of your documentation updates, ensuring the linked 
`@sample` formatting elements display beautifully.

---

## Licensing

By contributing to Ihawu, you agree that your code contributions will be licensed completely free under the project's
**Apache License 2.0**.

