# Invoice Automation System

A full-stack invoice automation system built with:

- React + TypeScript + Vite
- Spring Boot
- PostgreSQL

## Features

- Upload invoices with metadata and a source file
- Bulk import multiple PDF invoices with drag-and-drop upload
- Extract invoice fields from text-based PDFs with Apache PDFBox
- Run an AI-ready invoice extraction pipeline with an OpenAI-compatible LLM client
- Normalize and validate extracted values before persistence
- Detect likely duplicate invoices before save
- View all uploaded invoices in a dashboard table
- Search invoices by vendor and status
- Mark invoices as `PENDING`, `APPROVED`, or `PAID`
- View dashboard totals for invoice count and amount
- Review extracted values, edit them, and save corrected invoice data
- REST API integration between the frontend and backend
- Clear monorepo-style folder structure

## Folder Structure

```text
invoice-automation-system/
  backend/
    src/main/java/com/invoiceautomation/backend/
      config/
      controller/
      dto/
      entity/
      repository/
      service/
    src/main/resources/
    pom.xml
  frontend/
    src/
      components/
      pages/
      services/
      types/
    package.json
  docker-compose.yml
  README.md
```

## Backend API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/invoices` | Upload a new invoice using `multipart/form-data` |
| `POST` | `/api/invoices/bulk-upload` | Upload multiple PDFs, extract invoice fields, detect duplicates, and return per-file processing results |
| `GET` | `/api/invoices` | List invoices, optionally filtered by `vendor` and `status` |
| `PUT` | `/api/invoices/{id}/review` | Save reviewed extracted fields for an imported invoice |
| `PATCH` | `/api/invoices/{id}/status` | Update an invoice status |
| `GET` | `/api/dashboard` | Fetch invoice totals and summary counts |

## AI Processing Pipeline

Bulk PDF import is text-first and batch-safe:

- Apache PDFBox extracts raw text from each uploaded PDF
- The backend sends extracted text to an OpenAI-compatible LLM client when `APP_LLM_ENABLED=true`
- The LLM JSON is normalized and validated
- Existing rule-based parsing is still used as a fallback/enrichment layer
- Duplicate detection checks:
  - same `vendorName` + `invoiceNumber`
  - or same `vendorName` + `totalAmount` + `invoiceDate`
- Valid invoices are persisted to PostgreSQL
- Each file gets its own `SUCCESS`, `DUPLICATE`, or `FAILED` result in the batch response

OCR is not enabled yet. If text extraction is empty or structured fields are incomplete, the invoice can still be stored with `needsReview=true` for manual correction.

## LLM Configuration

Set these backend environment variables to enable live LLM extraction:

- `APP_LLM_ENABLED=true`
- `APP_LLM_API_KEY=...`
- `APP_LLM_MODEL=gpt-4o-mini`
- `APP_LLM_BASE_URL=https://api.openai.com/v1`
- `APP_LLM_CHAT_COMPLETIONS_PATH=/chat/completions`
- `APP_LLM_TIMEOUT=30s`
- `APP_LLM_TEMPERATURE=0.1`

If `APP_LLM_ENABLED=false`, the system falls back to the existing rule-based parser so local development still works without secrets.

## Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 18+
- npm 9+
- Docker Desktop or a local PostgreSQL instance

## Step 1: Start PostgreSQL

Use the included Docker Compose file:

```bash
docker compose up -d postgres
```

This creates:

- database: `invoice_automation`
- username: `postgres`
- password: `postgres`

## Step 2: Run the Spring Boot Backend

From the project root:

```bash
cd backend
mvn spring-boot:run
```

Default backend configuration is in [backend/src/main/resources/application.properties](/Users/a0000/Desktop/invoice-automation-system/backend/src/main/resources/application.properties). You can override it with environment variables listed in [backend/.env.example](/Users/a0000/Desktop/invoice-automation-system/backend/.env.example).

The backend starts on:

```text
http://localhost:8080
```

Uploaded invoice files are stored in the local `backend/uploads` directory by default.

## Step 3: Run the React Frontend

In a new terminal:

```bash
cd frontend
npm install
npm run dev
```

Optional frontend environment variables are documented in [frontend/.env.example](/Users/a0000/Desktop/invoice-automation-system/frontend/.env.example).

The frontend starts on:

```text
http://localhost:5173
```

## Data Flow

1. The React frontend submits invoice uploads to the Spring Boot API.
2. Bulk PDF import sends multiple files to `/api/invoices/bulk-upload`.
3. Spring Boot extracts raw text, calls the LLM service, normalizes the JSON, checks duplicates, stores valid invoices in PostgreSQL, and saves uploaded PDFs on disk.
4. The bulk upload response includes per-file results plus any saved invoices so the review table can populate immediately.
5. The review table shows extracted values so users can correct them and save with `/api/invoices/{id}/review`.
6. The dashboard and invoice list fetch data through REST endpoints.
7. Status updates are sent with `PATCH` requests and reflected in the dashboard totals.

## Notes

- CORS is preconfigured for `http://localhost:5173`.
- The UI is built as a single dashboard page to keep the main workflow fast.
- Bulk import currently prioritizes text-based PDFs and does not perform OCR fallback.
