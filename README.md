# invoice-automation-system-supply-chain

Full-stack operations workspace built with:

- React + TypeScript + Vite
- Spring Boot 3 + Java 17
- PostgreSQL

> Disclaimer: All data, documents, names, and examples in this repository are synthetic or manually written for learning and demonstration purposes only. They do not contain real private, confidential, or production customer information.

The repository now contains six modules:

- `Invoices`: upload, extract, review, deduplicate, and export invoice records
- `Supply Chain`: create orders, record milestone timestamps, calculate expected dates, detect SLA breaches, and surface alerts
- `Analytics Service`: read invoice and supply-chain tables with Pandas and generate operational reports in JSON and CSV
- `AI Service`: consume analytics-service outputs and generate grounded operational summaries, bottleneck insights, delay explanations, and recommended actions
- `Delivery Image Service`: OCR delivery photos and extract logistics fields such as location, date, PO, entry note, item name, and quantity
- `Micro-Frontends Demo`: Webpack Module Federation demo with a host shell plus independent invoice, supply-chain, and operations-dashboard remotes

## Supply Chain Module

Business flow:

`PO_RECEIVED -> PRODUCTION_COMPLETED -> SHIPPED -> ARRIVED_PORT -> CUSTOMS_CLEARED -> DELIVERED`

Default SLA rules:

| From | To | Target |
| --- | --- | --- |
| `PO_RECEIVED` | `PRODUCTION_COMPLETED` | 30 days |
| `PRODUCTION_COMPLETED` | `SHIPPED` | 5 days |
| `SHIPPED` | `ARRIVED_PORT` | 23 days |
| `ARRIVED_PORT` | `CUSTOMS_CLEARED` | 7 days |
| `CUSTOMS_CLEARED` | `DELIVERED` | 2 days |

What the module provides:

- order creation, update, and deletion
- milestone timestamp recording in sequence
- expected milestone date calculation from recorded timestamps
- order health classification: `ON_TIME`, `AT_RISK`, `DELAYED`
- persisted alert generation for at-risk and breached milestones
- dashboard counts for on-time, delayed, at-risk, delivered, and open alerts
- order detail timeline with actual vs expected milestone dates
- editable SLA rule table
- automatic seed data for SLA rules and sample orders on first boot

Backend tables:

- `orders`
- `order_milestones`
- `sla_rules`
- `alerts`

Frontend pages:

- `/supply-chain/dashboard`
- `/supply-chain/orders`
- `/supply-chain/orders/:orderId`
- `/supply-chain/alerts`
- `/supply-chain/sla-rules`

The existing invoice dashboard remains available at `/invoices`.

## API Summary

### Invoice API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/invoices` | Upload a new invoice using `multipart/form-data` |
| `POST` | `/api/invoices/bulk-upload` | Bulk import PDF invoices and return per-file results |
| `GET` | `/api/invoices` | List invoices with optional `vendor` and `status` filters |
| `GET` | `/api/invoices/export` | Export filtered invoices as Excel |
| `PUT` | `/api/invoices/{id}/review` | Save reviewed invoice extraction fields |
| `PATCH` | `/api/invoices/{id}/status` | Update invoice status |
| `GET` | `/api/dashboard` | Fetch invoice dashboard totals |

### Supply Chain API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/supply-chain/dashboard` | Supply-chain dashboard counts |
| `GET` | `/api/supply-chain/orders` | List orders with optional `search` and `healthStatus` filters |
| `GET` | `/api/supply-chain/orders/{id}` | Get one order with full milestone timeline |
| `POST` | `/api/supply-chain/orders` | Create an order and its `PO_RECEIVED` milestone |
| `PUT` | `/api/supply-chain/orders/{id}` | Update order metadata and `PO_RECEIVED` timestamp |
| `PUT` | `/api/supply-chain/orders/{id}/milestones/{milestoneType}` | Record or update a milestone timestamp |
| `DELETE` | `/api/supply-chain/orders/{id}` | Delete an order and its alerts |
| `GET` | `/api/supply-chain/alerts` | List alerts, optionally filtered by `status` |
| `GET` | `/api/supply-chain/sla-rules` | List SLA rules |
| `PUT` | `/api/supply-chain/sla-rules/{id}` | Update target and warning days |

### Analytics Service API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/reports/daily` | Daily operational analytics report |
| `GET` | `/reports/weekly` | Weekly operational analytics report |
| `GET` | `/reports/summary` | All-time summary analytics report |
| `GET` | `/reports/orders/{id}` | Order-specific analytics report for grounded AI delay analysis |

Analytics endpoints support:

- default JSON responses
- CSV output via `?format=csv`
- optional file persistence via `?persist=true`

### AI Service API

The AI module is intentionally narrow. It is not a chat endpoint and does not call the business backend directly. It consumes only structured outputs exposed by `analytics-service` and turns them into operations analysis.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/ai/analyze-order` | Explain why a specific order is delayed using analytics facts only |
| `POST` | `/ai/daily-summary` | Generate a grounded daily operations summary |
| `POST` | `/ai/weekly-summary` | Generate a grounded weekly operations summary |
| `POST` | `/ai/recommendations` | Generate recommended actions from daily, weekly, or summary analytics |

### Delivery Image Service API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Health check for the delivery-image OCR service |
| `POST` | `/delivery-images/extract` | Upload a `jpg` / `jpeg` / `png` delivery image and extract logistics fields plus `raw_text` |

## Micro-Frontend Demo

The repository includes a separate `microfrontends/` workspace that demonstrates route-level micro-frontends with Webpack Module Federation.

Apps and ports:

| App | Purpose | Port |
| --- | --- | --- |
| `host-app` | Shell, layout, navigation, route composition | `3000` |
| `invoice-app` | Existing invoice page exposed as a remote | `3001` |
| `supply-chain-app` | Existing supply-chain routes exposed as a remote | `3002` |
| `dashboard-app` | Analytics and AI insight remote | `3003` |

Key characteristics:

- each app runs on its own webpack dev server
- the host loads remotes dynamically through Module Federation
- `react`, `react-dom`, and `react-router-dom` are shared as singletons
- the existing Spring Boot backend, analytics-service, and ai-service APIs are reused without endpoint redesign
- the original Vite frontend in `frontend/` remains available alongside the micro-frontend demo

## Local Setup

### Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 18+
- npm 9+
- Python 3.11+
- Docker Desktop or a local PostgreSQL server

### 1. Start PostgreSQL

From the repository root:

```bash
docker compose up -d postgres
```

This starts PostgreSQL with:

- database: `invoice_automation`
- username: `postgres`
- password: `postgres`

### 2. Run the backend

In a new terminal:

```bash
cd backend
mvn spring-boot:run
```

Default backend URL:

```text
http://localhost:8080
```

If port `8080` is already in use, run the backend on `8081` instead:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

Seed behavior:

- default SLA rules are inserted only when `sla_rules` is empty
- sample supply-chain orders are inserted only when `orders` is empty

Uploaded invoice files are stored in `backend/uploads`.

### 3. Run the frontend

In another terminal:

```bash
cd frontend
npm install
npm run dev
```

Default frontend URL:

```text
http://localhost:5173
```

If you started the backend on `8081`, point the frontend at that port:

```bash
cd frontend
VITE_API_BASE_URL=http://localhost:8081/api npm run dev
```

If your local environment prefers IPv6 loopback, this also works:

```bash
cd frontend
VITE_API_BASE_URL='http://[::1]:8081/api' npm run dev -- --host 127.0.0.1 --port 5173
```

Frontend AI integration defaults to:

```text
http://127.0.0.1:8001
```

If you run `ai-service` on another port, point Vite at it:

```bash
cd frontend
VITE_API_BASE_URL=http://localhost:8081/api VITE_AI_API_BASE_URL=http://127.0.0.1:8002 npm run dev
```

### 4. Run the analytics service

In another terminal:

```bash
cd analytics-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python app.py
```

Default analytics service URL:

```text
http://127.0.0.1:5001
```

The analytics service connects to the same PostgreSQL database as the backend. By default it reuses the backend-style datasource values:

- `SPRING_DATASOURCE_URL` or `jdbc:postgresql://localhost:5432/invoice_automation`
- `SPRING_DATASOURCE_USERNAME` or `postgres`
- `SPRING_DATASOURCE_PASSWORD` or `postgres`

You can also override the SQLAlchemy connection string directly:

```bash
export ANALYTICS_DATABASE_URL=postgresql+psycopg://postgres:postgres@localhost:5432/invoice_automation
python app.py
```

Useful analytics-service environment variables:

- `ANALYTICS_SERVICE_PORT=5001`
- `ANALYTICS_SERVICE_HOST=127.0.0.1`
- `ANALYTICS_REPORT_OUTPUT_DIR=analytics-service/generated-reports`
- `ANALYTICS_SCHEDULE_HOUR=6`
- `ANALYTICS_SCHEDULE_MINUTE=0`
- `ANALYTICS_SERVICE_TIMEZONE=America/Los_Angeles`
- `ANALYTICS_GENERATE_ON_STARTUP=true`

Scheduled output:

- the daily scheduler writes JSON and CSV files to `analytics-service/generated-reports/daily/`
- JSON and CSV files are named with the report type and generation date

### 5. Run the AI service

The AI service must start after `analytics-service`, because it reads analytics outputs over HTTP.

In another terminal:

```bash
cd ai-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python app.py
```

Default AI service URL:

```text
http://127.0.0.1:8001
```

Useful `ai-service` environment variables:

- `AI_SERVICE_HOST=127.0.0.1`
- `AI_SERVICE_PORT=8001`
- `AI_SERVICE_ANALYTICS_BASE_URL=http://127.0.0.1:5001`
- `AI_SERVICE_ANALYTICS_BASE_URLS=http://127.0.0.1:5001,http://localhost:5001,http://[::1]:5001`
- `AI_SERVICE_FRONTEND_ORIGINS=http://localhost:5173,http://127.0.0.1:5173,http://[::1]:5173`
- `AI_SERVICE_REQUEST_TIMEOUT_SECONDS=10`

If `analytics-service` is running on a non-default port, start AI like this:

```bash
cd ai-service
source .venv/bin/activate
AI_SERVICE_ANALYTICS_BASE_URL=http://127.0.0.1:5002 python app.py
```

### 6. Run the micro-frontends demo

The micro-frontends expect:

- Spring Boot backend running on `http://localhost:8081` or `http://localhost:8080`
- analytics-service running on `http://127.0.0.1:5001`
- ai-service running on `http://127.0.0.1:8001`

Install the workspace dependencies once:

```bash
cd microfrontends
npm install
```

If you want to run the browser-based host verification step later, install Playwright's Chromium binary once:

```bash
cd microfrontends
npx playwright install chromium
```

Start all four micro-frontends:

If Spring Boot is running on `8081`:

```bash
cd microfrontends
VITE_API_BASE_URL=http://localhost:8081/api \
VITE_AI_API_BASE_URL=http://127.0.0.1:8001 \
VITE_ANALYTICS_API_BASE_URL=http://127.0.0.1:5001 \
npm run dev:all
```

If Spring Boot is running on `8080`:

```bash
cd microfrontends
VITE_API_BASE_URL=http://localhost:8080/api \
VITE_AI_API_BASE_URL=http://127.0.0.1:8001 \
VITE_ANALYTICS_API_BASE_URL=http://127.0.0.1:5001 \
npm run dev:all
```

Standalone URLs:

- [http://localhost:3000](http://localhost:3000) for `host-app`
- [http://localhost:3001](http://localhost:3001) for `invoice-app`
- [http://localhost:3002](http://localhost:3002) for `supply-chain-app`
- [http://localhost:3003](http://localhost:3003) for `dashboard-app`

Useful micro-frontend commands:

```bash
cd microfrontends
npm run build:all
npm run verify:host
```

`npm run verify:host` opens headless Chromium and verifies that the host can render:

- the operations dashboard remote
- the invoice remote
- the supply-chain remote

The verification script expects the four webpack dev servers to already be running.

## First-Run Verification

After backend, analytics-service, ai-service, and the chosen frontend runtime are running:

- Open [http://localhost:5173/supply-chain/dashboard](http://localhost:5173/supply-chain/dashboard)
- Open [http://localhost:5173/invoices](http://localhost:5173/invoices)
- Open [http://127.0.0.1:5001/reports/summary](http://127.0.0.1:5001/reports/summary)
- Open [http://127.0.0.1:8001/health](http://127.0.0.1:8001/health)
- Open [http://localhost:3000/operations/overview](http://localhost:3000/operations/overview) when the micro-frontends demo is running

Sample API checks:

```bash
curl http://localhost:8080/api/supply-chain/dashboard
curl http://localhost:8080/api/supply-chain/orders
curl http://localhost:8080/api/supply-chain/alerts
curl http://127.0.0.1:5001/reports/daily
curl http://127.0.0.1:5001/reports/orders/2
curl http://127.0.0.1:5001/reports/weekly?format=csv
curl http://127.0.0.1:5001/reports/summary?persist=true
curl http://127.0.0.1:8001/health
curl -X POST http://127.0.0.1:8001/ai/daily-summary -H 'Content-Type: application/json' -d '{}'
curl -X POST http://127.0.0.1:8001/ai/weekly-summary -H 'Content-Type: application/json' -d '{}'
curl -X POST http://127.0.0.1:8001/ai/analyze-order -H 'Content-Type: application/json' -d '{"orderId":2}'
curl -X POST http://127.0.0.1:8001/ai/recommendations -H 'Content-Type: application/json' -d '{"scope":"weekly"}'
curl http://localhost:3001/remoteEntry.js
curl http://localhost:3002/remoteEntry.js
curl http://localhost:3003/remoteEntry.js
```

If you are using port `8081`, replace `8080` with `8081`.

Frontend AI verification:

- The supply-chain dashboard now includes an `AI Operations Insight` panel grounded in the daily analytics report.
- Each supply-chain order detail page now includes an `AI Delay Explanation` section grounded in the order analytics report plus weekly analytics context.

## Invoice Extraction Notes

Bulk PDF import is text-first:

- Apache PDFBox extracts raw text from each uploaded PDF
- the backend can call an OpenAI-compatible LLM when `APP_LLM_ENABLED=true`
- heuristic parsing remains available as fallback
- likely duplicates are detected before persistence
- incomplete extractions can still be saved with `needsReview=true`

LLM-related environment variables:

- `APP_LLM_ENABLED=true`
- `APP_LLM_API_KEY=...`
- `APP_LLM_MODEL=gpt-4o-mini`
- `APP_LLM_BASE_URL=https://api.openai.com/v1`
- `APP_LLM_CHAT_COMPLETIONS_PATH=/chat/completions`
- `APP_LLM_TIMEOUT=30s`
- `APP_LLM_TEMPERATURE=0.1`

## Development Notes

- CORS allows the Vite frontend by default
- supply-chain alerts are synchronized from current milestone state during module reads and writes
- the UI uses React Router so supply-chain pages and the invoice module share one application shell
- the analytics service lives in `analytics-service/` and uses Flask, Pandas, SQLAlchemy, and APScheduler
