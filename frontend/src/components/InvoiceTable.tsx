import type { Invoice, InvoiceStatus } from '../types/invoice';

interface InvoiceTableProps {
  invoices: Invoice[];
  loading: boolean;
  updatingId: number | null;
  deletingSelection: boolean;
  exporting: boolean;
  duplicateInvoiceIds: Set<number>;
  selectedInvoiceIds: Set<number>;
  onToggleSelection: (invoiceId: number) => void;
  onToggleSelectAllDuplicates: () => void;
  onDeleteSelected: () => Promise<void>;
  onExport: () => Promise<void>;
  onStatusChange: (invoiceId: number, status: InvoiceStatus) => Promise<void>;
}

const dateFormatter = new Intl.DateTimeFormat('en-US', {
  dateStyle: 'medium',
});

const statusLabels: Record<InvoiceStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  PAID: 'Paid',
};

function formatMoney(amount: number | null, currency: string | null) {
  if (amount === null) {
    return '—';
  }

  const resolvedCurrency = currency ?? 'USD';

  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: resolvedCurrency,
    }).format(amount);
  } catch {
    return `${resolvedCurrency} ${amount.toFixed(2)}`;
  }
}

function formatDate(value: string | null) {
  if (!value) {
    return '—';
  }

  return dateFormatter.format(new Date(`${value}T00:00:00`));
}

export function InvoiceTable({
  invoices,
  loading,
  updatingId,
  deletingSelection,
  exporting,
  duplicateInvoiceIds,
  selectedInvoiceIds,
  onToggleSelection,
  onToggleSelectAllDuplicates,
  onDeleteSelected,
  onExport,
  onStatusChange,
}: InvoiceTableProps) {
  const duplicateCount = duplicateInvoiceIds.size;
  const selectedCount = selectedInvoiceIds.size;
  const allDuplicatesSelected =
    duplicateCount > 0 && Array.from(duplicateInvoiceIds).every((id) => selectedInvoiceIds.has(id));

  return (
    <section className="panel table-card">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Invoices</p>
          <h3>Current invoice queue</h3>
        </div>
        <div className="table-actions">
          <span className="helper-text">
            {invoices.length} records
            {duplicateCount > 0 ? ` · ${duplicateCount} duplicates` : ''}
          </span>
          <button className="ghost-button" type="button" disabled={exporting} onClick={() => void onExport()}>
            {exporting ? 'Exporting...' : 'Export Excel'}
          </button>
          <button
            className="ghost-button danger-button"
            type="button"
            disabled={deletingSelection || selectedCount === 0}
            onClick={() => void onDeleteSelected()}
          >
            {deletingSelection ? 'Deleting...' : `Delete Selected${selectedCount ? ` (${selectedCount})` : ''}`}
          </button>
        </div>
      </div>

      <div className="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>
                <input
                  type="checkbox"
                  aria-label="Select all duplicate invoices"
                  checked={allDuplicatesSelected}
                  disabled={duplicateCount === 0}
                  onChange={() => onToggleSelectAllDuplicates()}
                />
              </th>
              <th>Vendor</th>
              <th>Invoice #</th>
              <th>Total</th>
              <th>Issue Date</th>
              <th>File</th>
              <th>Parse</th>
              <th>Review</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {!loading && invoices.length === 0 ? (
              <tr>
                <td className="empty-state" colSpan={9}>
                  No invoices match the current filters.
                </td>
              </tr>
            ) : null}

            {invoices.map((invoice) => {
              const isDuplicate = duplicateInvoiceIds.has(invoice.id);

              return (
                <tr key={invoice.id}>
                  <td>
                    {isDuplicate ? (
                      <input
                        type="checkbox"
                        aria-label={`Select invoice ${invoice.id}`}
                        checked={selectedInvoiceIds.has(invoice.id)}
                        onChange={() => onToggleSelection(invoice.id)}
                      />
                    ) : null}
                  </td>
                  <td>
                    <div className="invoice-primary-cell">
                      <strong>{invoice.vendorName ?? invoice.vendor ?? '—'}</strong>
                      {isDuplicate ? <span className="duplicate-pill">Duplicate</span> : null}
                    </div>
                  </td>
                  <td>{invoice.invoiceNumber ?? '—'}</td>
                  <td>{formatMoney(invoice.totalAmount ?? invoice.amount, invoice.currency)}</td>
                  <td>{formatDate(invoice.issueDate ?? invoice.invoiceDate)}</td>
                  <td>{invoice.originalFileName}</td>
                  <td>
                    <div className="review-meta">
                      <span className={`parse-pill ${invoice.parseStatus.toLowerCase()}`}>
                        {invoice.parseStatus}
                      </span>
                      <span>
                        {invoice.parseConfidence === null
                          ? 'n/a'
                          : `${Math.round(invoice.parseConfidence * 100)}%`}
                      </span>
                    </div>
                  </td>
                  <td>
                    <span className={invoice.needsReview ? 'needs-review' : 'review-complete'}>
                      {invoice.needsReview ? 'Needs review' : 'Reviewed'}
                    </span>
                  </td>
                  <td>
                    <div className="status-cell">
                      <span className={`status-pill ${invoice.status.toLowerCase()}`}>
                        {statusLabels[invoice.status]}
                      </span>
                      <select
                        value={invoice.status}
                        disabled={updatingId === invoice.id}
                        onChange={(event) =>
                          void onStatusChange(invoice.id, event.target.value as InvoiceStatus)
                        }
                      >
                        <option value="PENDING">Pending</option>
                        <option value="APPROVED">Approved</option>
                        <option value="PAID">Paid</option>
                      </select>
                    </div>
                  </td>
                </tr>
              );
            })}

            {loading && invoices.length === 0 ? (
              <tr>
                <td className="empty-state" colSpan={9}>
                  Loading invoices...
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </section>
  );
}
