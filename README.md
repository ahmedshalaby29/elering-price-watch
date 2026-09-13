# ⚡ Elering Price Watch

[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-316192?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![CI](https://img.shields.io/badge/CI-GitHub_Actions-2088FF?logo=githubactions&logoColor=white)](.github/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

A production-quality Spring Boot service that tracks **Estonian Nord Pool day-ahead electricity prices** via the public [Elering API](https://dashboard.elering.ee/api) and alerts users when prices drop below (or rise above) user-configured thresholds.

---

## Why This Exists

The Baltic electricity market is one of Europe's most **volatile**, with hourly prices that can swing from -50 EUR/MWh (negative — overproduction) to 4000+ EUR/MWh (grid crises). For Estonian households:

- **EV owners** can save 60–80% by charging during the 3 cheapest overnight hours.
- **Flexible loads** (washing machine, dishwasher, heat pump, water heater) benefit enormously from hourly awareness.
- The **Elering dashboard** (government-operated TSO) publishes the next day's 24 hourly prices every afternoon — this service fetches and stores them so you don't have to check manually.

This service automates: *"Send me a Telegram/email when tonight's price drops below 30 EUR/MWh so I can schedule my EV charge."*

---

## Architecture

```mermaid
graph TD
    subgraph External
        E[Elering API\nhttps://dashboard.elering.ee/api]
    end

    subgraph Elering Price Watch
        SCH[PriceFetchScheduler\n@Scheduled 11:00 UTC daily]
        SVC[PriceFetchService\norchestrate fetch + upsert]
        CLI[EleringClientImpl\nRestClient + Spring Retry]
        PRI[PriceService\ncheapest window algorithm]
        ALT[AlertService\nthreshold evaluation + cooldown]
        NOT[NotificationService\nemail / webhook / Telegram]
        PC[PriceController\nGET /api/prices/*]
        AC[AlertController\nPOST /api/alerts]
        ADM[AdminController\nPOST /api/admin/trigger-fetch]
        REP[(PostgreSQL\nhourly_prices\nalert_subscriptions)]
        FE[Static Frontend\nChart.js dashboard]
    end

    subgraph Consumers
        U1[Household User\nbrowser]
        U2[Home Automation\nwebhook]
        U3[Email / Telegram\nnotification]
    end

    SCH --> SVC
    SVC --> CLI --> E
    CLI -->|EleringPriceRecord| SVC
    SVC --> REP
    SVC --> ALT --> NOT --> U3
    PC --> PRI --> REP
    AC --> REP
    ADM --> SVC
    FE -->|fetch /api/prices/*| PC
    U1 --> FE
    U2 --> AC
```

### Package Structure

```
com.elering.pricewatch
├── config/           RestClient, OpenAPI, Mail beans
├── client/           EleringClient interface + RestClient impl (Spring Retry)
├── domain/
│   ├── entity/       HourlyPrice, AlertSubscription (JPA)
│   └── enums/        Zone (EE/FI/LV/LT), AlertDirection, NotificationChannel
├── repository/       Spring Data JPA repositories + stats projection
├── service/          PriceService (algorithms), PriceFetchService,
│                     AlertService, NotificationService, AlertSubscriptionService
├── scheduler/        PriceFetchScheduler (@Scheduled)
├── controller/       PriceController, AlertController, AdminController
├── dto/              Request/response DTOs (separate from entities)
├── mapper/           MapStruct mappers with sensitive-field masking
└── exception/        GlobalExceptionHandler (RFC 7807 ProblemDetail)
```

---

## Features

| Feature | Details |
|---|---|
| **Scheduled fetch** | Daily at ~14:00 EET (11:00 UTC), fetches next-day prices for EE/FI/LV/LT |
| **Today/Tomorrow prices** | GET endpoints returning 24 hourly prices per zone |
| **Cheapest hours** | Two algorithms: contiguous O(n) sliding window + non-contiguous O(n log n) sort |
| **Price statistics** | Min/max/avg over any date range |
| **Multi-zone compare** | Side-by-side prices for all 4 Baltic zones |
| **Alert subscriptions** | Email, Webhook (Slack/n8n/Zapier), Telegram Bot API |
| **VAT-inclusive price** | Consumer EUR/kWh = raw EUR/MWh ÷ 1000 × 1.22 |
| **Upsert pattern** | Re-fetching data updates existing records (handles Elering corrections) |
| **24h notification cooldown** | Prevents spam; each subscription notifies at most once per day |
| **RFC 7807 errors** | All errors return `application/problem+json` |
| **Swagger UI** | Full OpenAPI 3 documentation at `/swagger-ui.html` |
| **Spring Actuator** | Health, metrics, info at `/actuator/*` |
| **Chart.js dashboard** | Dark-mode price curve with cheapest-hours highlighting at `/` |

---

## Quick Start

### One-command Docker run

```bash
git clone https://github.com/your-org/elering-price-watch.git
cd elering-price-watch
docker-compose up --build
```

The app starts on **http://localhost:8080**. PostgreSQL starts first with a health check — the app waits until it's ready.

| URL | What |
|---|---|
| `http://localhost:8080/` | Chart.js price dashboard |
| `http://localhost:8080/swagger-ui.html` | Swagger API docs |
| `http://localhost:8080/actuator/health` | Health check |
| `http://localhost:8080/api-docs` | OpenAPI JSON |

### Local development (without Docker)

**Prerequisites:** Java 21, Maven 3.9+, PostgreSQL 16

```bash
# 1. Start PostgreSQL (Docker one-liner)
docker run -d --name pricewatch-pg \
  -e POSTGRES_DB=pricewatch \
  -e POSTGRES_USER=pricewatch \
  -e POSTGRES_PASSWORD=pricewatch \
  -p 5432:5432 postgres:16-alpine

# 2. Run the app
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/pricewatch \
SPRING_DATASOURCE_USERNAME=pricewatch \
SPRING_DATASOURCE_PASSWORD=pricewatch \
mvn spring-boot:run

# 3. Manually trigger a fetch for today + tomorrow
curl -X POST "http://localhost:8080/api/admin/trigger-fetch"
curl -X POST "http://localhost:8080/api/admin/trigger-fetch?date=$(date -v+1d +%Y-%m-%d)"
```

---

## API Examples

### Get today's prices (Estonia zone)

```bash
curl http://localhost:8080/api/prices/today?zone=EE | jq .
```

```json
[
  {
    "hourStart": "2025-01-15T10:00:00Z",
    "zone": "EE",
    "priceEurMwh": 89.35,
    "priceWithVat": 0.108967,
    "localHourLabel": "12:00–13:00",
    "fetchedAt": "2025-01-14T11:02:00Z"
  },
  ...
]
```

### Find the cheapest 3 non-contiguous hours today

```bash
curl "http://localhost:8080/api/prices/cheapest?zone=EE&hours=3&contiguous=false"
```

```json
{
  "zone": "EE",
  "requestedHours": 3,
  "contiguous": false,
  "totalCostEurMwh": 81.75,
  "avgPriceEurMwh": 27.25,
  "estimatedConsumerPriceEurKwh": 0.033245,
  "hours": [
    { "hourStart": "2025-01-15T06:00:00Z", "localHourLabel": "08:00–09:00", "priceEurMwh": 25.50 },
    { "hourStart": "2025-01-15T05:00:00Z", "localHourLabel": "07:00–08:00", "priceEurMwh": 28.75 },
    { "hourStart": "2025-01-15T04:00:00Z", "localHourLabel": "06:00–07:00", "priceEurMwh": 27.50 }
  ]
}
```

### Find the cheapest 4-hour contiguous block (e.g. for a long EV charge)

```bash
curl "http://localhost:8080/api/prices/cheapest?zone=EE&hours=4&contiguous=true"
```

### Price statistics for January 2025

```bash
curl "http://localhost:8080/api/prices/stats?zone=EE&from=2025-01-01T00:00:00Z&to=2025-02-01T00:00:00Z"
```

```json
{
  "zone": "EE",
  "from": "2025-01-01T00:00:00Z",
  "to": "2025-02-01T00:00:00Z",
  "minPrice": 12.50,
  "maxPrice": 268.90,
  "avgPrice": 89.3412,
  "priceCount": 744
}
```

### Compare all 4 Baltic zones for tomorrow

```bash
curl "http://localhost:8080/api/prices/compare?zones=EE,FI,LV,LT" | jq 'keys'
```

### Register an alert subscription (Email)

```bash
curl -X POST http://localhost:8080/api/alerts \
  -H "Content-Type: application/json" \
  -d '{
    "zone": "EE",
    "thresholdEurMwh": 30.00,
    "direction": "BELOW",
    "channel": "EMAIL",
    "email": "myemail@example.com",
    "label": "EV charger - charge when cheap"
  }'
```

### Register a Telegram alert

```bash
curl -X POST http://localhost:8080/api/alerts \
  -H "Content-Type: application/json" \
  -d '{
    "zone": "EE",
    "thresholdEurMwh": 200.00,
    "direction": "ABOVE",
    "channel": "TELEGRAM",
    "telegramChatId": "-1001234567890",
    "label": "Price spike warning"
  }'
```

### Manually trigger a fetch

```bash
# Fetch tomorrow's prices right now (don't wait for the scheduler)
curl -X POST "http://localhost:8080/api/admin/trigger-fetch?date=2025-01-16"
```

---

## Configuration Reference

All configuration is environment-variable driven (12-factor app):

| Environment Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/pricewatch` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `pricewatch` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `pricewatch` | DB password |
| `PRICE_FETCH_CRON` | `0 0 11 * * *` | Cron for scheduled fetch (UTC) |
| `SCHEDULING_ZONES` | `EE,FI,LV,LT` | Zones to fetch |
| `ELERING_API_BASE_URL` | `https://dashboard.elering.ee/api` | Override API URL (testing) |
| `ELERING_TIMEOUT_SECONDS` | `10` | HTTP timeout for Elering API |
| `ELERING_RETRY_MAX_ATTEMPTS` | `3` | Max retry attempts |
| `NOTIFICATIONS_EMAIL_ENABLED` | `false` | Enable email notifications |
| `MAIL_HOST` | `smtp.gmail.com` | SMTP server |
| `MAIL_PORT` | `587` | SMTP port |
| `MAIL_USERNAME` | _(empty)_ | SMTP username |
| `MAIL_PASSWORD` | _(empty)_ | SMTP password |
| `NOTIFICATIONS_WEBHOOK_ENABLED` | `true` | Enable webhook notifications |
| `NOTIFICATIONS_TELEGRAM_ENABLED` | `false` | Enable Telegram notifications |
| `TELEGRAM_BOT_TOKEN` | _(empty)_ | Telegram Bot API token |
| `SERVER_PORT` | `8080` | HTTP server port |

---

## Testing

```bash
# Unit tests (no Docker required)
mvn test

# All tests including integration (Testcontainers — requires Docker)
mvn verify

# Coverage report (opens in target/site/jacoco/index.html)
mvn verify jacoco:report
open target/site/jacoco/index.html

# Lint check
mvn checkstyle:check
```

### Test structure

| Test | Type | What it covers |
|---|---|---|
| `PriceServiceTest` | Unit | Sliding-window contiguous algorithm, non-contiguous sort, edge cases (negative prices, boundary N, ties) |
| `AlertServiceTest` | Unit | Threshold comparison (BELOW/ABOVE, boundary, negatives), 24h cooldown logic |
| `PriceControllerIT` | Integration | Full HTTP round-trip for all 4 price endpoints, 404 and error format |
| `PriceFetchSchedulerIT` | Integration | WireMock-stubbed Elering API, upsert idempotency, 5xx error isolation |
| `HourlyPriceRepositoryIT` | Integration | All custom JPA queries, stats projection, unique constraint enforcement |

---

## Enabling Notifications

### Telegram (Recommended — free, instant)

1. Message [@BotFather](https://t.me/BotFather) on Telegram → `/newbot`
2. Copy the token → set `TELEGRAM_BOT_TOKEN=<your-token>`
3. Get your chat ID: message [@userinfobot](https://t.me/userinfobot) → copy the ID
4. Set `NOTIFICATIONS_TELEGRAM_ENABLED=true`
5. Register a subscription with `"channel": "TELEGRAM"` and `"telegramChatId": "<your-chat-id>"`

### Email (Gmail example)

1. Enable 2FA in Google Account → generate an App Password
2. Set `MAIL_USERNAME=you@gmail.com`, `MAIL_PASSWORD=<app-password>`
3. Set `NOTIFICATIONS_EMAIL_ENABLED=true`

### Webhook (Slack, n8n, Zapier, Home Assistant)

No config needed (enabled by default). Register a subscription with `"channel": "WEBHOOK"` and `"webhookUrl": "https://..."`. The service POSTs JSON like:

```json
{
  "event": "price_alert",
  "zone": "EE",
  "direction": "BELOW",
  "thresholdEurMwh": 30.00,
  "triggeringHours": [
    { "hourStart": "2025-01-15T05:00:00Z", "priceEurMwh": 25.50, "priceWithVat": 0.031110 }
  ]
}
```

---

## Data Model

```sql
-- Hourly prices (unique per hour + zone)
hourly_prices (
    id UUID PK,
    hour_start TIMESTAMPTZ NOT NULL,   -- UTC
    zone VARCHAR(4) NOT NULL,           -- EE / FI / LV / LT
    price_eur_mwh NUMERIC(10,4),       -- Nord Pool wholesale
    price_with_vat NUMERIC(10,6),      -- EUR/kWh incl. 22% EE VAT
    fetched_at TIMESTAMPTZ,
    UNIQUE (hour_start, zone)
)

-- Alert subscriptions
alert_subscriptions (
    id UUID PK,
    email VARCHAR(320),
    webhook_url VARCHAR(2048),
    telegram_chat_id VARCHAR(64),
    threshold_eur_mwh NUMERIC(10,4),
    direction VARCHAR(8),              -- BELOW / ABOVE
    zone VARCHAR(4),
    active BOOLEAN DEFAULT TRUE,
    channel VARCHAR(16),               -- EMAIL / WEBHOOK / TELEGRAM
    label VARCHAR(255),
    created_at TIMESTAMPTZ,
    last_notified_at TIMESTAMPTZ       -- 24h cooldown
)
```

---

## Technology Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 3.3 |
| Web | Spring MVC, Spring Validation |
| Persistence | Spring Data JPA, Hibernate, Flyway |
| Database | PostgreSQL 16 |
| HTTP Client | Spring 6 RestClient + Spring Retry |
| Code generation | MapStruct, Lombok |
| API docs | springdoc-openapi (Swagger UI) |
| Observability | Spring Actuator |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers, WireMock |
| Build | Maven 3.9, JaCoCo, Checkstyle |
| Packaging | Docker (multi-stage), Docker Compose |
| CI | GitHub Actions |

---

## Contributing

1. Fork → create a feature branch (`feature/my-feature`)
2. Write tests for new logic
3. `mvn verify` must pass (including integration tests)
4. Open a PR — CI runs automatically

---

## License

MIT — see [LICENSE](LICENSE).
