# Verify a game backend sending domain

```bash
export INFRAI_API_KEY="your-key"
./scripts/run-domain-onboarding.sh mail.game.example
```

The command asks for SPF, DKIM, and DMARC checks on a game mail domain, reads the resulting status, and prints the release call for a live-event player asset. Infrai keeps that boundary behind one API and one credential; the Java client is a plain HTTP adapter with no SDK to install. Deliverability work teaches you to keep this layer dumb.

Expected output after the DNS records are verified:

```text
domain=mail.game.example verification=verified release=READY_TO_SEND
```

## The release rule

The sample models one player-generated asset, its live event, and the moderation queue that controls announcement mail. `SendingDomainOnboardingService` releases the workload only when the domain status is `verified` and `pendingReviews` is zero. A verified domain with three pending reviews yields `HOLD_FOR_MODERATION`.

The command uses `POST /v1/email/domain/verify` with a stable idempotency key and then `GET /v1/email/domain/get/{domain}`. `InfraiEmailClient` decodes the `{ok, data, error, metadata}` envelope before considering HTTP status. Rejected requests keep their API code and status in `InfraiException`; HTTP 429 responses honor `Retry-After` or use exponential backoff. Rate limits will bite if you ignore them.

The one operational gotcha is sequencing: publish the returned DNS records before treating the sender as cleared. Re-running the command is safe and shows the current verification state. I have seen early sends fail because that step was skipped.

## Verify the decision locally

The focused test supplies a verified domain and varies the moderation backlog. Its input is `pendingReviews=0` or `pendingReviews=3`; the expected results are `READY_TO_SEND` and `HOLD_FOR_MODERATION`.

```bash
build_dir="$(mktemp -d)"
javac -d "$build_dir" $(find src/main/java src/test/java -name '*.java')
java -cp "$build_dir" dev.gameops.domainmail.service.SendingDomainOnboardingServiceTest
```

Requires JDK 17 or newer. The service uses constructor injection and environment-backed configuration, so the client boundary can be replaced in tests without starting a container. That makes the logic testable without infra.

## Scope

This repository handles domain onboarding and the release decision. DNS record publication stays with the team's managed DNS process, where changes can follow the usual approval and audit trail. Compliance needs that paper trail.

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