import type { ReviewInvoiceDraft, ReviewInvoiceEditableField } from '../types/invoice';

interface ExtractedInvoiceReviewTableProps {
  rows: ReviewInvoiceDraft[];
  savingId: number | null;
  onChange: (invoiceId: number, field: ReviewInvoiceEditableField, value: string) => void;
  onSave: (invoiceId: number) => Promise<void>;
}

const parseLabels = {
  SUCCESS: 'Parsed',
  PARTIAL: 'Needs review',
  FAILED: 'Failed',
} as const;

function formatConfidence(confidence: number | null) {
  if (confidence === null) {
    return 'n/a';
  }

  return `${Math.round(confidence * 100)}%`;
}

export function ExtractedInvoiceReviewTable({
  rows,
  savingId,
  onChange,
  onSave,
}: ExtractedInvoiceReviewTableProps) {
  return (
    <section className="panel review-card">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Review Queue</p>
          <h3>Validate extracted values before final save</h3>
        </div>
        <span className="helper-text">{rows.length} imported files</span>
      </div>

      <div className="table-wrapper">
        <table className="review-table">
          <thead>
            <tr>
              <th>File</th>
              <th>Parse</th>
              <th>Invoice #</th>
              <th>Vendor</th>
              <th>Qty</th>
              <th>Unit Price</th>
              <th>Subtotal</th>
              <th>Tax</th>
              <th>Total</th>
              <th>Currency</th>
              <th>Issue Date</th>
              <th>Raw Text</th>
              <th>Save</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td className="empty-state" colSpan={13}>
                  Bulk-imported PDFs will appear here for review.
                </td>
              </tr>
            ) : null}

            {rows.map((row) => (
              <tr key={row.id}>
                <td>
                  <div className="review-meta">
                    <strong>{row.originalFileName}</strong>
                    <span className={row.needsReview ? 'needs-review' : 'review-complete'}>
                      {row.needsReview ? 'Needs review' : 'Ready'}
                    </span>
                  </div>
                </td>
                <td>
                  <div className="review-meta">
                    <span className={`parse-pill ${row.parseStatus.toLowerCase()}`}>
                      {parseLabels[row.parseStatus]}
                    </span>
                    <span>{formatConfidence(row.parseConfidence)}</span>
                  </div>
                </td>
                <td>
                  <input
                    value={row.invoiceNumber}
                    onChange={(event) => onChange(row.id, 'invoiceNumber', event.target.value)}
                    placeholder="INV-2048"
                  />
                </td>
                <td>
                  <input
                    value={row.vendorName}
                    onChange={(event) => onChange(row.id, 'vendorName', event.target.value)}
                    placeholder="Vendor name"
                  />
                </td>
                <td>
                  <input
                    value={row.quantity}
                    onChange={(event) => onChange(row.id, 'quantity', event.target.value)}
                    placeholder="0"
                  />
                </td>
                <td>
                  <input
                    value={row.unitPrice}
                    onChange={(event) => onChange(row.id, 'unitPrice', event.target.value)}
                    placeholder="0.00"
                  />
                </td>
                <td>
                  <input
                    value={row.subtotalAmount}
                    onChange={(event) => onChange(row.id, 'subtotalAmount', event.target.value)}
                    placeholder="0.00"
                  />
                </td>
                <td>
                  <input
                    value={row.taxAmount}
                    onChange={(event) => onChange(row.id, 'taxAmount', event.target.value)}
                    placeholder="0.00"
                  />
                </td>
                <td>
                  <input
                    value={row.totalAmount}
                    onChange={(event) => onChange(row.id, 'totalAmount', event.target.value)}
                    placeholder="0.00"
                  />
                </td>
                <td>
                  <input
                    value={row.currency}
                    onChange={(event) => onChange(row.id, 'currency', event.target.value)}
                    placeholder="USD"
                  />
                </td>
                <td>
                  <input
                    type="date"
                    value={row.issueDate}
                    onChange={(event) => onChange(row.id, 'issueDate', event.target.value)}
                  />
                </td>
                <td>
                  <details className="raw-preview">
                    <summary>Preview</summary>
                    <pre>{row.rawExtractedText || 'No text extracted from this PDF.'}</pre>
                  </details>
                </td>
                <td>
                  <button
                    className="ghost-button review-save-button"
                    type="button"
                    disabled={savingId === row.id}
                    onClick={() => void onSave(row.id)}
                  >
                    {savingId === row.id ? 'Saving...' : 'Save'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
