import { startTransition, useDeferredValue, useEffect, useState } from 'react';
import { BatchUploadResultsPanel } from '../components/BatchUploadResultsPanel';
import { BulkPdfImport } from '../components/BulkPdfImport';
import { DashboardCards } from '../components/DashboardCards';
import { ExtractedInvoiceReviewTable } from '../components/ExtractedInvoiceReviewTable';
import { InvoiceFilters } from '../components/InvoiceFilters';
import { InvoiceForm } from '../components/InvoiceForm';
import { InvoiceTable } from '../components/InvoiceTable';
import {
  bulkUploadInvoices,
  deleteInvoices,
  exportInvoices,
  getDashboardSummary,
  getInvoices,
  saveReviewedInvoice,
  updateInvoiceStatus,
  uploadInvoice,
} from '../services/api';
import type {
  BatchUploadResult,
  DashboardSummary,
  Invoice,
  InvoiceFilters as InvoiceFiltersModel,
  InvoiceReviewUpdatePayload,
  InvoiceStatus,
  InvoiceUploadPayload,
  ReviewInvoiceDraft,
  ReviewInvoiceEditableField,
} from '../types/invoice';

function toFormValue(value: number | string | null | undefined) {
  return value === null || value === undefined ? '' : String(value);
}

function normalizeDateInputValue(value: string | null | undefined) {
  if (!value) {
    return '';
  }

  const trimmedValue = value.trim();
  const isoMatch = trimmedValue.match(/^(\d{4})[-/](\d{2})[-/](\d{2})$/);
  if (isoMatch) {
    return `${isoMatch[1]}-${isoMatch[2]}-${isoMatch[3]}`;
  }

  const slashMatch = trimmedValue.match(/^(\d{1,2})[-/](\d{1,2})[-/](\d{4})$/);
  if (slashMatch) {
    return `${slashMatch[3]}-${slashMatch[1].padStart(2, '0')}-${slashMatch[2].padStart(2, '0')}`;
  }

  return trimmedValue;
}

function toReviewDraft(invoice: Invoice): ReviewInvoiceDraft {
  return {
    id: invoice.id,
    originalFileName: invoice.originalFileName,
    parseStatus: invoice.parseStatus,
    parseConfidence: invoice.parseConfidence,
    needsReview: invoice.needsReview,
    rawExtractedText: invoice.rawExtractedText,
    invoiceNumber: invoice.invoiceNumber ?? '',
    vendorName: invoice.vendorName ?? invoice.vendor ?? '',
    quantity: toFormValue(invoice.quantity),
    unitPrice: toFormValue(invoice.unitPrice),
    subtotalAmount: toFormValue(invoice.subtotalAmount),
    taxAmount: toFormValue(invoice.taxAmount),
    totalAmount: toFormValue(invoice.totalAmount ?? invoice.amount),
    currency: invoice.currency ?? '',
    issueDate: normalizeDateInputValue(invoice.issueDate ?? invoice.invoiceDate),
  };
}

function toNullableNumber(value: string) {
  const normalizedValue = value.trim();
  if (!normalizedValue) {
    return null;
  }

  const parsedValue = Number(normalizedValue);
  return Number.isNaN(parsedValue) ? null : parsedValue;
}

function toNullableString(value: string) {
  const normalizedValue = value.trim();
  return normalizedValue ? normalizedValue : null;
}

function toReviewPayload(draft: ReviewInvoiceDraft): InvoiceReviewUpdatePayload {
  return {
    invoiceNumber: toNullableString(draft.invoiceNumber),
    vendorName: toNullableString(draft.vendorName),
    quantity: toNullableNumber(draft.quantity),
    unitPrice: toNullableNumber(draft.unitPrice),
    subtotalAmount: toNullableNumber(draft.subtotalAmount),
    taxAmount: toNullableNumber(draft.taxAmount),
    totalAmount: toNullableNumber(draft.totalAmount),
    currency: toNullableString(draft.currency),
    issueDate: toNullableString(normalizeDateInputValue(draft.issueDate)),
  };
}

function mergeReviewRows(currentRows: ReviewInvoiceDraft[], invoices: Invoice[]) {
  const existingById = new Map(currentRows.map((row) => [row.id, row]));
  return invoices
    .filter((invoice) => invoice.needsReview)
    .map((invoice) => {
      const existingRow = existingById.get(invoice.id);
      return existingRow ? { ...existingRow, ...toReviewDraft(invoice) } : toReviewDraft(invoice);
    });
}

function toDuplicateKey(invoice: Invoice) {
  const invoiceNumber = invoice.invoiceNumber?.trim().toUpperCase();
  if (invoiceNumber) {
    return `invoice:${invoiceNumber}`;
  }

  const issueDate = invoice.issueDate ?? invoice.invoiceDate ?? '';
  const amount = invoice.totalAmount ?? invoice.amount ?? '';
  const fileName = invoice.originalFileName.trim().toLowerCase();
  return `fallback:${fileName}|${issueDate}|${amount}`;
}

function getDuplicateInvoiceIds(invoices: Invoice[]) {
  const invoiceIds = new Set<number>();
  const buckets = new Map<string, number[]>();

  invoices.forEach((invoice) => {
    const key = toDuplicateKey(invoice);
    const bucket = buckets.get(key) ?? [];
    bucket.push(invoice.id);
    buckets.set(key, bucket);
  });

  buckets.forEach((ids) => {
    if (ids.length > 1) {
      ids.forEach((id) => invoiceIds.add(id));
    }
  });

  return invoiceIds;
}

function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function InvoiceDashboardPage() {
  const [filters, setFilters] = useState<InvoiceFiltersModel>({
    vendor: '',
    status: '',
  });
  const deferredVendor = useDeferredValue(filters.vendor);

  const [dashboardSummary, setDashboardSummary] = useState<DashboardSummary | null>(null);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [bulkSubmitting, setBulkSubmitting] = useState(false);
  const [updatingId, setUpdatingId] = useState<number | null>(null);
  const [reviewSavingId, setReviewSavingId] = useState<number | null>(null);
  const [reviewRows, setReviewRows] = useState<ReviewInvoiceDraft[]>([]);
  const [selectedInvoiceIds, setSelectedInvoiceIds] = useState<Set<number>>(new Set());
  const [deletingSelection, setDeletingSelection] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [refreshToken, setRefreshToken] = useState(0);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [latestBatchUpload, setLatestBatchUpload] = useState<BatchUploadResult | null>(null);

  const duplicateInvoiceIds = getDuplicateInvoiceIds(invoices);

  useEffect(() => {
    setSelectedInvoiceIds((currentSelection) => {
      const nextSelection = new Set<number>();
      currentSelection.forEach((id) => {
        if (duplicateInvoiceIds.has(id)) {
          nextSelection.add(id);
        }
      });
      return nextSelection;
    });
  }, [invoices]);

  useEffect(() => {
    let cancelled = false;

    async function loadData() {
      setLoading(true);
      setErrorMessage(null);

      try {
        const [invoiceData, summaryData] = await Promise.all([
          getInvoices({ vendor: deferredVendor, status: filters.status }),
          getDashboardSummary(),
        ]);

        if (!cancelled) {
          setInvoices(invoiceData);
          setDashboardSummary(summaryData);
          setReviewRows((currentRows) => mergeReviewRows(currentRows, invoiceData));
        }
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(
            error instanceof Error ? error.message : 'Unable to load invoice dashboard.',
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadData();

    return () => {
      cancelled = true;
    };
  }, [deferredVendor, filters.status, refreshToken]);

  async function handleUpload(payload: InvoiceUploadPayload) {
    setSubmitting(true);
    setErrorMessage(null);

    try {
      await uploadInvoice(payload);
      startTransition(() => {
        setRefreshToken((current) => current + 1);
      });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to upload invoice.');
      throw error;
    } finally {
      setSubmitting(false);
    }
  }

  async function handleBulkUpload(files: File[]) {
    setBulkSubmitting(true);
    setErrorMessage(null);

    try {
      const response = await bulkUploadInvoices(files);
      setLatestBatchUpload(response);
      setReviewRows((currentRows) => mergeReviewRows(currentRows, response.savedInvoices));
      startTransition(() => {
        setRefreshToken((current) => current + 1);
      });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to import PDF invoices.');
      throw error;
    } finally {
      setBulkSubmitting(false);
    }
  }

  function handleReviewFieldChange(
    invoiceId: number,
    field: ReviewInvoiceEditableField,
    value: string,
  ) {
    setReviewRows((currentRows) =>
      currentRows.map((row) => (row.id === invoiceId ? { ...row, [field]: value } : row)),
    );
  }

  async function handleReviewSave(invoiceId: number) {
    const row = reviewRows.find((candidate) => candidate.id === invoiceId);
    if (!row) {
      return;
    }

    setReviewSavingId(invoiceId);
    setErrorMessage(null);

    try {
      const response = await saveReviewedInvoice(invoiceId, toReviewPayload(row));
      setInvoices((currentInvoices) =>
        currentInvoices.map((candidate) => (candidate.id === invoiceId ? response : candidate)),
      );
      setReviewRows((currentRows) => {
        if (!response.needsReview) {
          return currentRows.filter((candidate) => candidate.id !== invoiceId);
        }

        return currentRows.map((candidate) =>
          candidate.id === invoiceId ? toReviewDraft(response) : candidate,
        );
      });
      startTransition(() => {
        setRefreshToken((current) => current + 1);
      });
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : 'Unable to save reviewed invoice values.',
      );
    } finally {
      setReviewSavingId(null);
    }
  }

  async function handleStatusChange(invoiceId: number, status: InvoiceStatus) {
    setUpdatingId(invoiceId);
    setErrorMessage(null);

    try {
      const response = await updateInvoiceStatus(invoiceId, status);
      setInvoices((currentInvoices) =>
        currentInvoices.map((candidate) => (candidate.id === invoiceId ? response : candidate)),
      );
      startTransition(() => {
        setRefreshToken((current) => current + 1);
      });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to update invoice status.');
    } finally {
      setUpdatingId(null);
    }
  }

  function handleToggleSelection(invoiceId: number) {
    setSelectedInvoiceIds((currentSelection) => {
      const nextSelection = new Set(currentSelection);
      if (nextSelection.has(invoiceId)) {
        nextSelection.delete(invoiceId);
      } else {
        nextSelection.add(invoiceId);
      }
      return nextSelection;
    });
  }

  function handleToggleSelectAllDuplicates() {
    setSelectedInvoiceIds((currentSelection) => {
      const nextSelection = new Set(currentSelection);
      const allSelected = Array.from(duplicateInvoiceIds).every((id) => nextSelection.has(id));

      if (allSelected) {
        duplicateInvoiceIds.forEach((id) => nextSelection.delete(id));
      } else {
        duplicateInvoiceIds.forEach((id) => nextSelection.add(id));
      }

      return nextSelection;
    });
  }

  async function handleDeleteSelected() {
    const idsToDelete = Array.from(selectedInvoiceIds);
    if (idsToDelete.length === 0) {
      return;
    }

    setDeletingSelection(true);
    setErrorMessage(null);

    try {
      await deleteInvoices(idsToDelete);
      setSelectedInvoiceIds(new Set());
      setReviewRows((currentRows) =>
        currentRows.filter((candidate) => !idsToDelete.includes(candidate.id)),
      );
      startTransition(() => {
        setRefreshToken((current) => current + 1);
      });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to delete invoices.');
    } finally {
      setDeletingSelection(false);
    }
  }

  async function handleExport() {
    setExporting(true);
    setErrorMessage(null);

    try {
      const { blob, fileName } = await exportInvoices({
        vendor: deferredVendor,
        status: filters.status,
      });
      downloadBlob(blob, fileName);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to export invoices.');
    } finally {
      setExporting(false);
    }
  }

  return (
    <section className="module-stack">
      <section className="hero">
        <div>
          <p className="eyebrow">Invoice Automation System Supply Chain</p>
          <h1>Track uploads, approvals, and payment flow from one dashboard.</h1>
          <p className="hero-copy">
            Upload invoice files, filter by vendor or status, and keep the finance pipeline moving
            with a single React and Spring Boot application.
          </p>
        </div>

        <div className="hero-badge">
          <span>REST API</span>
          <strong>React + Spring Boot + PostgreSQL</strong>
        </div>
      </section>

      {errorMessage ? <div className="error-banner">{errorMessage}</div> : null}

      <DashboardCards summary={dashboardSummary} />

      <BulkPdfImport onUpload={handleBulkUpload} submitting={bulkSubmitting} />

      <BatchUploadResultsPanel result={latestBatchUpload} />

      <div className="content-grid">
        <InvoiceForm onSubmit={handleUpload} submitting={submitting} />
        <InvoiceFilters
          filters={filters}
          onChange={setFilters}
          onRefresh={() =>
            startTransition(() => {
              setRefreshToken((current) => current + 1);
            })
          }
        />
      </div>

      <ExtractedInvoiceReviewTable
        rows={reviewRows}
        savingId={reviewSavingId}
        onChange={handleReviewFieldChange}
        onSave={handleReviewSave}
      />

      <InvoiceTable
        invoices={invoices}
        loading={loading}
        updatingId={updatingId}
        deletingSelection={deletingSelection}
        exporting={exporting}
        duplicateInvoiceIds={duplicateInvoiceIds}
        selectedInvoiceIds={selectedInvoiceIds}
        onToggleSelection={handleToggleSelection}
        onToggleSelectAllDuplicates={handleToggleSelectAllDuplicates}
        onDeleteSelected={handleDeleteSelected}
        onExport={handleExport}
        onStatusChange={handleStatusChange}
      />
    </section>
  );
}
