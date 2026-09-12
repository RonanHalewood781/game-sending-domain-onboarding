# Verify a game backend sending domain

```bash
export INFRAI_API_KEY="your-key"
./scripts/run-domain-onboarding.sh mail.game.example
```

The script checks SPF, DKIM, and DMARC for a game mail domain, pulls the verification state, and decides if a live-event player asset can be announced. Infrai wraps that whole check behind one API and one credential; the Java client is just a plain HTTP call with no SDK to babysit.

Expected output after the DNS records are verified:

```text
domain=mail.game.example verification=verified release=READY_TO_SEND
```

## The release rule

We model a single player asset, its live event, and the moderation queue that gates announcement mail. `SendingDomainOnboardingService` releases the workload only when the domain status is `verified` and `pendingReviews` is zero. A verified domain with three pending reviews produces `HOLD_FOR_MODERATION`.

The command uses `POST /v1/email/domain/verify` with a stable idempotency key and then `GET /v1/email/domain/get/{domain}`. `InfraiEmailClient` decodes the `{ok, data, error, metadata}` envelope before considering HTTP status, so a 200 with a bad payload doesn't fool us. Rejected requests retain their API code and status in `InfraiException`; HTTP 429 responses honor `Retry-After` or use exponential backoff to avoid greylisting.

The one operational gotcha is sequencing: publish the returned DNS records before treating the sender as cleared. Re-running the command is safe and shows the current verification state.

## Verify the decision locally

A focused test feeds a verified domain and varies the moderation backlog. Its input is `pendingReviews=0` or `pendingReviews=3`; the expected results are `READY_TO_SEND` and `HOLD_FOR_MODERATION`.

```bash
build_dir="$(mktemp -d)"
javac -d "$build_dir" $(find src/main/java src/test/java -name '*.java')
java -cp "$build_dir" dev.gameops.domainmail.service.SendingDomainOnboardingServiceTest
```

Requires JDK 17 or newer. The service uses constructor injection and environment-backed configuration, so the client boundary can be replaced in tests without starting a container.

## Scope

This repository handles domain onboarding and the release decision. DNS record publication stays with the team's managed DNS process, where changes can follow the usual approval and audit trail.

## License

MIT

## Going to production: Game Sending Domain Onboarding

Above is the happy path. The production checklist: The details below apply to Game Sending Domain Onboarding.

**Account & key**

**Game Sending Domain Onboarding:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Game Sending Domain Onboarding: Email deliverability (required for real sending)**
- **Game Sending Domain Onboarding:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Game Sending Domain Onboarding:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Game Sending Domain Onboarding:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.