import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getSupplyChainAlerts } from '../services/api';
import type { AlertStatus, SupplyChainAlert } from '../types/supplyChain';
import { formatDateTime } from '../utils/formatters';

export function SupplyChainAlertsPage() {
  const [status, setStatus] = useState<AlertStatus | ''>('OPEN');
  const [alerts, setAlerts] = useState<SupplyChainAlert[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadAlerts() {
      setLoading(true);
      setErrorMessage(null);

      try {
        const response = await getSupplyChainAlerts(status);
        if (!cancelled) {
          setAlerts(response);
        }
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(error instanceof Error ? error.message : 'Unable to load alerts.');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadAlerts();

    return () => {
      cancelled = true;
    };
  }, [status]);

  return (
    <section className="module-stack">
      {errorMessage ? <div className="error-banner">{errorMessage}</div> : null}

      <section className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Alert Center</p>
            <h2>SLA breach and warning alerts</h2>
            <p className="hero-copy compact-copy">
              Alerts are synchronized from the current milestone state whenever the module loads.
            </p>
          </div>

          <label className="field compact-field">
            <span>Status</span>
            <select value={status} onChange={(event) => setStatus(event.target.value as AlertStatus | '')}>
              <option value="OPEN">Open</option>
              <option value="RESOLVED">Resolved</option>
            </select>
          </label>
        </div>

        <div className="stack-list">
          {alerts.length > 0 ? (
            alerts.map((alert) => (
              <article key={alert.id} className={`alert-card ${alert.severity.toLowerCase()}`}>
                <div>
                  <div className="list-title-row">
                    <strong>{alert.title}</strong>
                    <span className={`status-pill ${alert.severity === 'CRITICAL' ? 'danger' : 'warning'}`}>
                      {alert.status === 'OPEN' ? 'Open' : 'Resolved'}
                    </span>
                  </div>
                  <p className="list-meta">
                    {alert.orderNumber} · {alert.customerName} · {alert.milestoneLabel}
                  </p>
                  <p>{alert.message}</p>
                  <p className="helper-text">
                    Triggered {formatDateTime(alert.triggeredAt)}
                    {alert.resolvedAt ? ` · Resolved ${formatDateTime(alert.resolvedAt)}` : ''}
                  </p>
                </div>
                <Link to={`/supply-chain/orders/${alert.orderId}`} className="ghost-button page-link">
                  Open order
                </Link>
              </article>
            ))
          ) : (
            <p className="empty-state">
              {loading ? 'Loading alerts...' : 'No alerts match the selected status.'}
            </p>
          )}
        </div>
      </section>
    </section>
  );
}
