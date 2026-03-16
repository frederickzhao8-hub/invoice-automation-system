import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { HealthPill } from '../components/supplyChain/HealthPill';
import {
  getAiDailySummary,
  getSupplyChainAlerts,
  getSupplyChainDashboard,
  getSupplyChainOrders,
} from '../services/api';
import type { AiInsight } from '../types/ai';
import type {
  SupplyChainAlert,
  SupplyChainDashboard,
  SupplyChainOrderSummary,
} from '../types/supplyChain';
import { formatDateTimeCompact, formatStageLabel } from '../utils/formatters';

export function SupplyChainDashboardPage() {
  const [dashboard, setDashboard] = useState<SupplyChainDashboard | null>(null);
  const [orders, setOrders] = useState<SupplyChainOrderSummary[]>([]);
  const [alerts, setAlerts] = useState<SupplyChainAlert[]>([]);
  const [aiInsight, setAiInsight] = useState<AiInsight | null>(null);
  const [aiLoading, setAiLoading] = useState(true);
  const [aiError, setAiError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadDashboard() {
      setLoading(true);
      setErrorMessage(null);

      try {
        const [dashboardResponse, ordersResponse, alertsResponse] = await Promise.all([
          getSupplyChainDashboard(),
          getSupplyChainOrders({ search: '', healthStatus: '' }),
          getSupplyChainAlerts('OPEN'),
        ]);

        if (!cancelled) {
          setDashboard(dashboardResponse);
          setOrders(ordersResponse);
          setAlerts(alertsResponse);
        }
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(
            error instanceof Error
              ? error.message
              : 'Unable to load the supply-chain dashboard.',
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadDashboard();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function loadAiInsight() {
      setAiLoading(true);
      setAiError(null);

      try {
        const response = await getAiDailySummary();
        if (!cancelled) {
          setAiInsight(response);
        }
      } catch (error) {
        if (!cancelled) {
          setAiError(
            error instanceof Error ? error.message : 'Unable to load AI operations insight.',
          );
        }
      } finally {
        if (!cancelled) {
          setAiLoading(false);
        }
      }
    }

    void loadAiInsight();

    return () => {
      cancelled = true;
    };
  }, []);

  const attentionOrders = orders
    .filter((order) => order.healthStatus !== 'ON_TIME')
    .sort((left, right) => {
      const leftDate = left.nextExpectedAt ? new Date(left.nextExpectedAt).getTime() : Number.MAX_SAFE_INTEGER;
      const rightDate = right.nextExpectedAt ? new Date(right.nextExpectedAt).getTime() : Number.MAX_SAFE_INTEGER;
      return leftDate - rightDate;
    })
    .slice(0, 5);

  const upcomingOrders = orders
    .filter((order) => order.nextExpectedAt)
    .sort((left, right) => new Date(left.nextExpectedAt ?? 0).getTime() - new Date(right.nextExpectedAt ?? 0).getTime())
    .slice(0, 5);

  return (
    <section className="module-stack">
      <section className="hero module-hero">
        <div>
          <p className="eyebrow">Supply Chain Module</p>
          <h2>Track milestone execution, SLA exposure, and shipping handoffs from PO to delivery.</h2>
          <p className="hero-copy">
            This module calculates expected dates from live milestone timestamps, flags orders that
            are drifting toward breach, and persists alerts for operational follow-up.
          </p>
        </div>

        <div className="hero-badge">
          <span>Workflow</span>
          <strong>PO Received → Production → Shipped → Port → Customs → Delivered</strong>
        </div>
      </section>

      {errorMessage ? <div className="error-banner">{errorMessage}</div> : null}

      <div className="stats-grid supply-stats-grid">
        <article className="stat-card accent-card">
          <span>Total Orders</span>
          <h2>{dashboard?.totalOrders ?? (loading ? '...' : 0)}</h2>
          <p className="helper-text">Seeded with sample orders on first boot.</p>
        </article>
        <article className="stat-card">
          <span>On Time</span>
          <h2>{dashboard?.onTimeOrders ?? (loading ? '...' : 0)}</h2>
          <p className="helper-text">Orders currently within SLA thresholds.</p>
        </article>
        <article className="stat-card">
          <span>At Risk</span>
          <h2>{dashboard?.atRiskOrders ?? (loading ? '...' : 0)}</h2>
          <p className="helper-text">Orders inside their warning windows.</p>
        </article>
        <article className="stat-card">
          <span>Delayed</span>
          <h2>{dashboard?.delayedOrders ?? (loading ? '...' : 0)}</h2>
          <p className="helper-text">{dashboard?.openAlerts ?? 0} open alerts across the module.</p>
        </article>
      </div>

      <div className="content-grid dashboard-grid">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Attention Queue</p>
              <h3>Orders needing intervention</h3>
            </div>
            <Link to="/supply-chain/orders" className="ghost-button page-link">
              Open order list
            </Link>
          </div>

          <div className="stack-list">
            {attentionOrders.length > 0 ? (
              attentionOrders.map((order) => (
                <article key={order.id} className="list-card">
                  <div>
                    <div className="list-title-row">
                      <strong>{order.orderNumber}</strong>
                      <HealthPill value={order.healthStatus} />
                    </div>
                    <p className="list-meta">
                      {order.customerName} · {order.productName}
                    </p>
                    <p className="helper-text">
                      Next milestone: {order.nextMilestoneLabel ?? 'Completed'} · Due{' '}
                      {formatDateTimeCompact(order.nextExpectedAt)}
                    </p>
                  </div>
                  <Link to={`/supply-chain/orders/${order.id}`} className="ghost-button page-link">
                    View timeline
                  </Link>
                </article>
              ))
            ) : (
              <p className="empty-state">No orders are currently at risk or delayed.</p>
            )}
          </div>
        </section>

        <section className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Upcoming</p>
              <h3>Next expected milestones</h3>
            </div>
          </div>

          <div className="stack-list">
            {upcomingOrders.length > 0 ? (
              upcomingOrders.map((order) => (
                <article key={order.id} className="deadline-card">
                  <div>
                    <strong>{order.orderNumber}</strong>
                    <p className="list-meta">{order.nextMilestoneLabel ?? 'Completed'}</p>
                  </div>
                  <div className="deadline-meta">
                    <span>{formatDateTimeCompact(order.nextExpectedAt)}</span>
                  </div>
                </article>
              ))
            ) : (
              <p className="empty-state">No upcoming milestones available.</p>
            )}
          </div>
        </section>
      </div>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">AI Operations Insight</p>
            <h3>Grounded daily analysis</h3>
            <p className="hero-copy compact-copy">
              This panel is generated only from structured analytics outputs, not free-form chat.
            </p>
          </div>
        </div>

        {aiError ? <div className="error-banner">{aiError}</div> : null}

        {aiLoading ? (
          <p className="empty-state">Loading AI operations analysis...</p>
        ) : aiInsight ? (
          <div className="ai-insight-grid">
            <article className="insight-card">
              <span className="helper-text">Narrative</span>
              <p className="insight-narrative">{aiInsight.narrative}</p>
              <p className="helper-text">
                Generated {formatDateTimeCompact(aiInsight.generatedAt)}
              </p>
            </article>

            <article className="insight-card">
              <span className="helper-text">Most Common Bottleneck</span>
              <h4>
                {aiInsight.mostCommonBottleneckStage
                  ? formatStageLabel(aiInsight.mostCommonBottleneckStage)
                  : 'No active bottleneck'}
              </h4>
              <ul className="insight-list">
                {aiInsight.keyFindings.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </article>

            <article className="insight-card">
              <span className="helper-text">Recommended Actions</span>
              <ul className="insight-list">
                {aiInsight.recommendedActions.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </article>
          </div>
        ) : (
          <p className="empty-state">No AI insight is available.</p>
        )}
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Alerts</p>
            <h3>Most recent open alerts</h3>
          </div>
          <Link to="/supply-chain/alerts" className="ghost-button page-link">
            Open alert list
          </Link>
        </div>

        <div className="stack-list">
          {alerts.length > 0 ? (
            alerts.slice(0, 6).map((alert) => (
              <article key={alert.id} className={`alert-card ${alert.severity.toLowerCase()}`}>
                <div>
                  <div className="list-title-row">
                    <strong>{alert.title}</strong>
                    <span className={`status-pill ${alert.severity === 'CRITICAL' ? 'danger' : 'warning'}`}>
                      {alert.severity === 'CRITICAL' ? 'Critical' : 'Warning'}
                    </span>
                  </div>
                  <p className="list-meta">
                    {alert.orderNumber} · {alert.customerName} · {alert.milestoneLabel}
                  </p>
                  <p>{alert.message}</p>
                </div>
                <Link to={`/supply-chain/orders/${alert.orderId}`} className="ghost-button page-link">
                  Open order
                </Link>
              </article>
            ))
          ) : (
            <p className="empty-state">No open alerts.</p>
          )}
        </div>
      </section>
    </section>
  );
}
