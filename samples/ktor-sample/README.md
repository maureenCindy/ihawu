# ihawu-ktor-sample

A runnable Ktor app that demonstrates [Ihawu](../../README.md) masking. A single `install(IhawuKtor)`
masks every `@IhawuResource` response for the caller's role — no masking or authorization code in the
handlers. Basic auth maps `manager`/`employee` to an `IhawuPrincipal`; an unauthenticated caller fails
closed to `{}`.

## Endpoints

| Route | Demonstrates |
| --- | --- |
| `GET /employee` | Flat `@IhawuResource` — MANAGER sees a redacted SSN; EMPLOYEE has salary + SSN hidden. |
| `GET /payment` | **Sealed** polymorphic `@IhawuResource` (0.4.0) — the concrete subtype is masked and the `type` discriminator preserved. |
| `GET /alert` | **OPEN** (non-sealed) polymorphic `@IhawuResource` (0.4.0) — its subtype is registered on the app's `Json` module so it can be resolved and masked. |

Polymorphism masking runs on the `ihawu-kotlinx` backend (JVM + JS). A **sealed** hierarchy needs no extra
wiring; an **OPEN** one requires registering its subtypes on the encoding `Json`'s `SerializersModule`
(see `appJson` in `Application.kt`).

## Run

```bash
./gradlew :samples:ktor-sample:run        # starts on :8080
./gradlew :samples:ktor-sample:test       # EmployeeMaskingTest + PolymorphicMaskingTest
```

Try it:

```bash
curl -u manager:secret localhost:8080/payment   # {"type":"card","holder":"Ada Lovelace","pan":"**** **** **** ****"}
curl -u manager:secret localhost:8080/alert     # {"type":"fraud","detail":"[redacted]"}
```
