export interface SupplyChainOrderFormValues {
  orderNumber: string;
  customerName: string;
  supplierName: string;
  productName: string;
  originCountry: string;
  destinationCountry: string;
  quantity: string;
  orderValue: string;
  notes: string;
  poReceivedAt: string;
}

interface OrderEditorProps {
  title: string;
  description: string;
  submitLabel: string;
  values: SupplyChainOrderFormValues;
  disabled?: boolean;
  submitting?: boolean;
  onChange: (field: keyof SupplyChainOrderFormValues, value: string) => void;
  onSubmit: () => void;
}

const fields: Array<{
  field: keyof SupplyChainOrderFormValues;
  label: string;
  type?: 'text' | 'number' | 'datetime-local';
  step?: string;
}> = [
  { field: 'orderNumber', label: 'Order number' },
  { field: 'customerName', label: 'Customer' },
  { field: 'supplierName', label: 'Supplier' },
  { field: 'productName', label: 'Product' },
  { field: 'originCountry', label: 'Origin country' },
  { field: 'destinationCountry', label: 'Destination country' },
  { field: 'quantity', label: 'Quantity', type: 'number', step: '0.01' },
  { field: 'poReceivedAt', label: 'PO received at', type: 'datetime-local' },
];

export function OrderEditor({
  title,
  description,
  submitLabel,
  values,
  disabled = false,
  submitting = false,
  onChange,
  onSubmit,
}: OrderEditorProps) {
  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Order Management</p>
          <h2>{title}</h2>
          <p className="hero-copy compact-copy">{description}</p>
        </div>
      </div>

      <div className="form-grid">
        {fields.map(({ field, label, type = 'text', step }) => (
          <label key={field} className="field">
            <span>{label}</span>
            <input
              type={type}
              step={step}
              value={values[field]}
              disabled={disabled || submitting}
              onChange={(event) => onChange(field, event.target.value)}
            />
          </label>
        ))}

        <label className="field field-full">
          <span>Notes</span>
          <textarea
            value={values.notes}
            disabled={disabled || submitting}
            onChange={(event) => onChange('notes', event.target.value)}
            rows={4}
          />
        </label>
      </div>

      <div className="panel-actions">
        <button type="button" className="primary-button" disabled={disabled || submitting} onClick={onSubmit}>
          {submitting ? 'Saving...' : submitLabel}
        </button>
      </div>
    </section>
  );
}
