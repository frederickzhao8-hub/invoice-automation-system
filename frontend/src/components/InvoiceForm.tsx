import { useState, type FormEvent } from 'react';
import type { InvoiceStatus, InvoiceUploadPayload } from '../types/invoice';

interface InvoiceFormProps {
  onSubmit: (payload: InvoiceUploadPayload) => Promise<void>;
  submitting: boolean;
}

interface InvoiceFormState {
  vendor: string;
  invoiceNumber: string;
  amount: string;
  invoiceDate: string;
  status: InvoiceStatus;
  file: File | null;
}

const initialState: InvoiceFormState = {
  vendor: '',
  invoiceNumber: '',
  amount: '',
  invoiceDate: '',
  status: 'PENDING',
  file: null,
};

export function InvoiceForm({ onSubmit, submitting }: InvoiceFormProps) {
  const [form, setForm] = useState<InvoiceFormState>(initialState);
  const [fileInputKey, setFileInputKey] = useState(0);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!form.file) {
      return;
    }

    await onSubmit({
      vendor: form.vendor.trim(),
      invoiceNumber: form.invoiceNumber.trim(),
      amount: form.amount,
      invoiceDate: form.invoiceDate,
      status: form.status,
      file: form.file,
    });

    setForm(initialState);
    setFileInputKey((current) => current + 1);
  }

  return (
    <section className="panel upload-card">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Manual Upload</p>
          <h3>Add a single invoice by hand</h3>
        </div>
        <span className="helper-text">Supported files: PDF, JPG, PNG</span>
      </div>

      <form className="upload-form" onSubmit={handleSubmit}>
        <div className="form-grid">
          <label className="field">
            <span>Vendor</span>
            <input
              type="text"
              placeholder="Acme Supplies"
              value={form.vendor}
              onChange={(event) => setForm({ ...form, vendor: event.target.value })}
              required
            />
          </label>

          <label className="field">
            <span>Invoice number</span>
            <input
              type="text"
              placeholder="INV-10024"
              value={form.invoiceNumber}
              onChange={(event) => setForm({ ...form, invoiceNumber: event.target.value })}
              required
            />
          </label>

          <label className="field">
            <span>Amount</span>
            <input
              type="number"
              min="0.01"
              step="0.01"
              placeholder="0.00"
              value={form.amount}
              onChange={(event) => setForm({ ...form, amount: event.target.value })}
              required
            />
          </label>

          <label className="field">
            <span>Invoice date</span>
            <input
              type="date"
              value={form.invoiceDate}
              onChange={(event) => setForm({ ...form, invoiceDate: event.target.value })}
              required
            />
          </label>

          <label className="field">
            <span>Status</span>
            <select
              value={form.status}
              onChange={(event) =>
                setForm({
                  ...form,
                  status: event.target.value as InvoiceStatus,
                })
              }
            >
              <option value="PENDING">Pending</option>
              <option value="APPROVED">Approved</option>
              <option value="PAID">Paid</option>
            </select>
          </label>

          <label className="field file-field">
            <span>Invoice file</span>
            <input
              key={fileInputKey}
              type="file"
              accept=".pdf,.jpg,.jpeg,.png"
              onChange={(event) =>
                setForm({
                  ...form,
                  file: event.target.files?.[0] ?? null,
                })
              }
              required
            />
          </label>
        </div>

        <button className="primary-button" type="submit" disabled={submitting}>
          {submitting ? 'Uploading...' : 'Upload invoice'}
        </button>
      </form>
    </section>
  );
}
