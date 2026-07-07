# MoneyLeaks API

Find forgotten subscriptions from bank statements.

MoneyLeaks is a personal backend project from my **10 apps in 10 weeks** experiment. The goal is to learn how to build, deploy, run, and maintain product-style software while solving a real problem: keeping track of recurring memberships and abonnementen (Netflix, gym, energy, streaming, etc.).

## What it does

1. Upload a bank statement CSV
2. Parse transactions
3. Detect recurring outgoing payments
4. Show active subscriptions and estimated monthly total

## Tech stack

- Java 21
- Spring Boot 3 (layered architecture)
- PostgreSQL + Flyway
- Docker Compose

## CSV format (MVP)

```csv
date,description,amount
2025-01-05,NETFLIX.COM Amsterdam,-15.99
2025-02-05,NETFLIX.COM Amsterdam,-15.99
```

- Supports `,` or `;` separators
- Date formats: `yyyy-MM-dd`, `dd-MM-yyyy`, `dd/MM/yyyy`
- Use negative amounts for outgoing payments (subscriptions)

## Run locally

```bash
docker compose up -d
./mvnw spring-boot:run
```

On Windows:

```powershell
docker compose up -d
mvn spring-boot:run
```

API starts on `http://localhost:8080`.

## API endpoints

- `POST /api/statements/upload` (multipart form field: `file`)
- `POST /api/statements/{id}/process`
- `GET /api/subscriptions`
- `GET /api/subscriptions/summary`

### Example flow

```bash
# 1) Upload statement
curl -F "file=@sample-data/statements/sample-statement.csv" http://localhost:8080/api/statements/upload

# 2) Process statement (use id from upload response)
curl -X POST http://localhost:8080/api/statements/1/process

# 3) View subscriptions
curl http://localhost:8080/api/subscriptions/summary
```

## Roadmap

- [ ] Dutch bank export parsers (ING/ABN/Rabobank)
- [ ] Price increase alerts
- [ ] Open Banking integration (later)
- [ ] Simple frontend

## Why this project

I wanted a niche app I would actually use myself, while practicing production-style backend patterns: idempotent uploads, recurring detection rules, observability-ready structure, and maintainable layered code.
