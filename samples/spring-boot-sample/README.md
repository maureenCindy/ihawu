# Ihawu Spring Boot Sample

A runnable Spring Boot app that demonstrates [Ihawu](../../README.md) masking end to end: a single
secured endpoint returns the **same** `@IhawuResource` employee record, and each role sees different
fields — enforced by Ihawu at serialization time, with no masking or authorization logic in the
controller.

Adding `ihawu-spring-boot-starter` to the classpath is all it takes to auto-configure masking; this
app only supplies the security users and the policy rules.

## Run it

From the repository root:

```bash
./gradlew :samples:spring-boot-sample:bootRun
```

The app starts on `http://localhost:8080`. Stop it with `Ctrl+C`.

## Choose a policy source

Ihawu supports two ways to supply masking rules, and this sample ships both behind Spring profiles.
The masked output is **identical** either way — only where the rules come from differs:

| Profile | Rules come from | How |
| --- | --- | --- |
| `provider` _(default)_ | a programmatic `ResourcePolicyProvider` bean | `PolicyConfig.kt` |
| `config` | `ihawu.policies` configuration | `application-config.yml` |

```bash
# default — programmatic provider
./gradlew :samples:spring-boot-sample:bootRun

# config-driven — same rules from ihawu.policies
./gradlew :samples:spring-boot-sample:bootRun --args='--spring.profiles.active=config'
```

`ProviderProfileMaskingTest` and `ConfigProfileMaskingTest` run the same assertions under each profile,
so the two sources are proven equivalent.

## Demo users

Three in-memory users, one per role, all with the password `password` (HTTP Basic):

| Username  | Password   | Role       |
| --------- | ---------- | ---------- |
| `hradmin` | `password` | `HR_ADMIN` |
| `manager` | `password` | `MANAGER`  |
| `employee`| `password` | `EMPLOYEE` |

## Try it — same request, different visibility

The endpoint is `GET /employees/{id}`. Call it as each user (`jq` optional, just for pretty output):

### HR Admin — sees the full record

No rules apply to `HR_ADMIN`, so nothing is masked (fail-open on missing policy).

```bash
curl -s -u hradmin:password localhost:8080/employees/42 | jq
```
```json
{
  "id": "42",
  "fullName": "Jane Doe",
  "email": "jane.doe@company.com",
  "salary": 145000.0,
  "socialSecurityNumber": "123-45-6789",
  "performanceNotes": "Exceeds expectations; promotion track."
}
```

### Manager — salary and notes visible, SSN redacted

```bash
curl -s -u manager:password localhost:8080/employees/42 | jq
```
```json
{
  "id": "42",
  "fullName": "Jane Doe",
  "email": "jane.doe@company.com",
  "salary": 145000.0,
  "socialSecurityNumber": "***-**-****",
  "performanceNotes": "Exceeds expectations; promotion track."
}
```

### Employee — sensitive fields removed entirely

`salary`, `socialSecurityNumber`, and `performanceNotes` are hidden (`HIDE` drops the field):

```bash
curl -s -u employee:password localhost:8080/employees/42 | jq
```
```json
{
  "id": "42",
  "fullName": "Jane Doe",
  "email": "jane.doe@company.com"
}
```

## How it works

- **`EmployeeResponse`** is annotated `@IhawuResource("employee")`, marking it for masking.
- **`EmployeeController`** returns the full record — it contains no authorization or masking code.
- **`PolicyConfig`** contributes a `ResourcePolicyProvider` bean with the per-role rules for the
  `employee` resource (`MANAGER` → redact SSN; `EMPLOYEE` → hide salary/SSN/notes). Supplying this
  bean overrides the starter's empty default.
- **`SecurityConfig`** wires the three demo users; Ihawu's principal bridge maps the authenticated
  `Authentication` to the role names the rules resolve against.

The masking difference between roles comes entirely from the policy — the handler is identical for all
callers.

## Run the tests

`ProviderProfileMaskingTest` and `ConfigProfileMaskingTest` pin the per-role masked JSON with MockMvc,
sharing their assertions via `AbstractEmployeeMaskingTest` so both policy sources are proven equivalent:

```bash
./gradlew :samples:spring-boot-sample:test
```
