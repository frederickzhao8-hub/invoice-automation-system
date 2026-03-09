import type { DashboardSummary } from '../types/invoice';

interface DashboardCardsProps {
  summary: DashboardSummary | null;
}

const currencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
});

export function DashboardCards({ summary }: DashboardCardsProps) {
  const data = summary ?? {
    totalInvoices: 0,
    totalAmount: 0,
    pendingCount: 0,
    approvedCount: 0,
    paidCount: 0,
  };

  return (
    <section className="stats-grid">
      <article className="stat-card">
        <p className="eyebrow">Dashboard</p>
        <h2>{data.totalInvoices}</h2>
        <span>Total invoices</span>
      </article>

      <article className="stat-card accent-card">
        <p className="eyebrow">Finance</p>
        <h2>{currencyFormatter.format(data.totalAmount)}</h2>
        <span>Total invoice amount</span>
      </article>

      <article className="stat-card">
        <p className="eyebrow">Workflow</p>
        <h2>{data.pendingCount}</h2>
        <span>Pending approvals</span>
      </article>

      <article className="stat-card compact-stats">
        <p className="eyebrow">Status mix</p>
        <div className="mini-status-grid">
          <div>
            <strong>{data.approvedCount}</strong>
            <span>Approved</span>
          </div>
          <div>
            <strong>{data.paidCount}</strong>
            <span>Paid</span>
          </div>
        </div>
      </article>
    </section>
  );
}

