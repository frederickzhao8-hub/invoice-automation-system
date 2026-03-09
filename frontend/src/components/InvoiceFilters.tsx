import type { InvoiceFilters as InvoiceFiltersModel, InvoiceStatus } from '../types/invoice';

interface InvoiceFiltersProps {
  filters: InvoiceFiltersModel;
  onChange: (nextFilters: InvoiceFiltersModel) => void;
  onRefresh: () => void;
}

const statuses: Array<{ value: '' | InvoiceStatus; label: string }> = [
  { value: '', label: 'All statuses' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'PAID', label: 'Paid' },
];

export function InvoiceFilters({ filters, onChange, onRefresh }: InvoiceFiltersProps) {
  return (
    <section className="panel controls-panel">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Search</p>
          <h3>Find invoices fast</h3>
        </div>
        <button className="ghost-button" type="button" onClick={onRefresh}>
          Refresh data
        </button>
      </div>

      <div className="filter-grid">
        <label className="field">
          <span>Vendor</span>
          <input
            type="text"
            value={filters.vendor}
            onChange={(event) => onChange({ ...filters, vendor: event.target.value })}
            placeholder="Search by vendor name"
          />
        </label>

        <label className="field">
          <span>Status</span>
          <select
            value={filters.status}
            onChange={(event) =>
              onChange({
                ...filters,
                status: event.target.value as '' | InvoiceStatus,
              })
            }
          >
            {statuses.map((status) => (
              <option key={status.label} value={status.value}>
                {status.label}
              </option>
            ))}
          </select>
        </label>
      </div>
    </section>
  );
}

