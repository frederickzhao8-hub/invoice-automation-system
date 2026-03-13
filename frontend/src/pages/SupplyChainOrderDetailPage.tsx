import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { HealthPill } from '../components/supplyChain/HealthPill';
import {
  getAiOrderAnalysis,
  getSupplyChainOrder,
  recordSupplyChainMilestone,
  updateSupplyChainOrder,
} from '../services/api';
import type { AiOrderAnalysis } from '../types/ai';
import type {
  MilestoneType,
  OrderMilestone,
  SupplyChainOrderDetail,
} from '../types/supplyChain';
import {
  formatCurrency,
  formatDateTime,
  formatNumber,
  formatStageLabel,
  toDateTimeLocalValue,
} from '../utils/formatters';
import { OrderEditor, type SupplyChainOrderFormValues } from '../components/supplyChain/OrderEditor';
import { createEmptyOrderFormValues, toOrderFormValues, toOrderPayload } from '../utils/supplyChainForm';

type MilestoneDrafts = Record<MilestoneType, { occurredAt: string; notes: string }>;

function buildMilestoneDrafts(order: SupplyChainOrderDetail): MilestoneDrafts {
  const nowValue = toDateTimeLocalValue(new Date().toISOString());

  return order.timeline.reduce(
    (drafts, milestone) => ({
      ...drafts,
      [milestone.milestoneType]: {
        occurredAt: toDateTimeLocalValue(milestone.actualAt) || nowValue,
        notes: milestone.notes ?? '',
      },
    }),
    {} as MilestoneDrafts,
  );
}

function canRecordMilestone(timeline: OrderMilestone[], index: number) {
  if (index === 0) {
    return true;
  }

  return timeline[index - 1]?.completed ?? false;
}

export function SupplyChainOrderDetailPage() {
  const { orderId } = useParams();
  const numericOrderId = Number(orderId);

  const [order, setOrder] = useState<SupplyChainOrderDetail | null>(null);
  const [formValues, setFormValues] = useState<SupplyChainOrderFormValues>(createEmptyOrderFormValues);
  const [milestoneDrafts, setMilestoneDrafts] = useState<MilestoneDrafts | null>(null);
  const [aiAnalysis, setAiAnalysis] = useState<AiOrderAnalysis | null>(null);
  const [aiLoading, setAiLoading] = useState(true);
  const [aiError, setAiError] = useState<string | null>(null);
  const [aiRefreshToken, setAiRefreshToken] = useState(0);
  const [loading, setLoading] = useState(true);
  const [savingOrder, setSavingOrder] = useState(false);
  const [savingMilestone, setSavingMilestone] = useState<MilestoneType | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (Number.isNaN(numericOrderId)) {
      setErrorMessage('Invalid order id.');
      setLoading(false);
      return;
    }

    let cancelled = false;

    async function loadOrder() {
      setLoading(true);
      setErrorMessage(null);

      try {
        const response = await getSupplyChainOrder(numericOrderId);
        if (!cancelled) {
          setOrder(response);
          setFormValues(toOrderFormValues(response));
          setMilestoneDrafts(buildMilestoneDrafts(response));
        }
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(error instanceof Error ? error.message : 'Unable to load order details.');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadOrder();

    return () => {
      cancelled = true;
    };
  }, [numericOrderId]);

  function applyOrderResponse(response: SupplyChainOrderDetail) {
    setOrder(response);
    setFormValues(toOrderFormValues(response));
    setMilestoneDrafts(buildMilestoneDrafts(response));
    setAiRefreshToken((current) => current + 1);
  }

  useEffect(() => {
    if (Number.isNaN(numericOrderId)) {
      return;
    }

    let cancelled = false;

    async function loadAiAnalysis() {
      setAiLoading(true);
      setAiError(null);

      try {
        const response = await getAiOrderAnalysis(numericOrderId);
        if (!cancelled) {
          setAiAnalysis(response);
        }
      } catch (error) {
        if (!cancelled) {
          setAiError(
            error instanceof Error ? error.message : 'Unable to load AI delay analysis.',
          );
        }
      } finally {
        if (!cancelled) {
          setAiLoading(false);
        }
      }
    }

    void loadAiAnalysis();

    return () => {
      cancelled = true;
    };
  }, [numericOrderId, aiRefreshToken]);

  function handleFormChange(field: keyof SupplyChainOrderFormValues, value: string) {
    setFormValues((current) => ({ ...current, [field]: value }));
  }

  function handleMilestoneDraftChange(
    milestoneType: MilestoneType,
    field: 'occurredAt' | 'notes',
    value: string,
  ) {
    setMilestoneDrafts((current) => {
      if (!current) {
        return current;
      }

      return {
        ...current,
        [milestoneType]: {
          ...current[milestoneType],
          [field]: value,
        },
      };
    });
  }

  async function handleSaveOrder() {
    if (!order) {
      return;
    }

    setSavingOrder(true);
    setErrorMessage(null);

    try {
      const response = await updateSupplyChainOrder(order.id, toOrderPayload(formValues));
      applyOrderResponse(response);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to save order.');
    } finally {
      setSavingOrder(false);
    }
  }

  async function handleSaveMilestone(milestoneType: MilestoneType) {
    if (!order || !milestoneDrafts) {
      return;
    }

    const draft = milestoneDrafts[milestoneType];
    if (!draft?.occurredAt) {
      setErrorMessage('Milestone timestamp is required.');
      return;
    }

    setSavingMilestone(milestoneType);
    setErrorMessage(null);

    try {
      const response = await recordSupplyChainMilestone(order.id, milestoneType, {
        occurredAt: draft.occurredAt,
        notes: draft.notes.trim() ? draft.notes.trim() : null,
      });
      applyOrderResponse(response);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to save milestone.');
    } finally {
      setSavingMilestone(null);
    }
  }

  if (loading) {
    return (
      <section className="module-stack">
        <section className="panel">
          <p className="empty-state">Loading order detail...</p>
        </section>
      </section>
    );
  }

  if (!order || !milestoneDrafts) {
    return (
      <section className="module-stack">
        <section className="panel">
          <p className="empty-state">{errorMessage ?? 'Order not found.'}</p>
        </section>
      </section>
    );
  }

  return (
    <section className="module-stack">
      {errorMessage ? <div className="error-banner">{errorMessage}</div> : null}

      <section className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Order Detail</p>
            <h2>{order.orderNumber}</h2>
            <p className="hero-copy compact-copy">
              {order.customerName} · {order.productName} · {formatCurrency(order.orderValue)}
            </p>
          </div>

          <div className="detail-actions">
            <HealthPill value={order.healthStatus} />
            <Link to="/supply-chain/orders" className="ghost-button page-link">
              Back to orders
            </Link>
          </div>
        </div>

        <div className="detail-summary-grid">
          <article className="stat-card">
            <span>Current milestone</span>
            <h2>{order.currentMilestoneLabel ?? 'Pending'}</h2>
            <p className="helper-text">Next: {order.nextMilestoneLabel ?? 'Completed'}</p>
          </article>
          <article className="stat-card">
            <span>Next expected</span>
            <h2>{formatDateTime(order.nextExpectedAt)}</h2>
            <p className="helper-text">Open alerts: {order.openAlertCount}</p>
          </article>
          <article className="stat-card">
            <span>Origin → Destination</span>
            <h2>
              {order.originCountry} → {order.destinationCountry}
            </h2>
            <p className="helper-text">Quantity: {formatNumber(order.quantity)}</p>
          </article>
        </div>
      </section>

      <OrderEditor
        title="Order metadata"
        description="Edit commercial fields and the PO Received timestamp without leaving the timeline."
        submitLabel="Save order"
        values={formValues}
        submitting={savingOrder}
        onChange={handleFormChange}
        onSubmit={handleSaveOrder}
      />

      <section className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">AI Delay Explanation</p>
            <h3>Analytics-grounded order analysis</h3>
            <p className="hero-copy compact-copy">
              The explanation below is generated only from analytics-service order and weekly report outputs.
            </p>
          </div>
        </div>

        {aiError ? <div className="error-banner">{aiError}</div> : null}

        {aiLoading ? (
          <p className="empty-state">Loading AI delay explanation...</p>
        ) : aiAnalysis ? (
          <div className="ai-insight-grid">
            <article className="insight-card">
              <span className="helper-text">Narrative</span>
              <p className="insight-narrative">{aiAnalysis.narrative}</p>
              <p className="helper-text">
                Delay stage:{' '}
                {aiAnalysis.delayStage ? formatStageLabel(aiAnalysis.delayStage) : 'Not currently delayed'} · Generated{' '}
                {formatDateTime(aiAnalysis.generatedAt)}
              </p>
            </article>

            <article className="insight-card">
              <span className="helper-text">Root Causes</span>
              <ul className="insight-list">
                {aiAnalysis.rootCauses.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </article>

            <article className="insight-card">
              <span className="helper-text">Recommended Actions</span>
              <ul className="insight-list">
                {aiAnalysis.recommendedActions.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </article>
          </div>
        ) : (
          <p className="empty-state">No AI delay explanation is available.</p>
        )}
      </section>

      <div className="content-grid detail-grid">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Timeline</p>
              <h3>Milestone execution</h3>
            </div>
          </div>

          <div className="timeline-stack">
            {order.timeline.map((milestone, index) => {
              const draft = milestoneDrafts[milestone.milestoneType];
              const locked = !milestone.completed && !canRecordMilestone(order.timeline, index);

              return (
                <article key={milestone.milestoneType} className="timeline-card">
                  <div className="timeline-card-header">
                    <div>
                      <strong>{milestone.milestoneLabel}</strong>
                      <p className="helper-text">
                        SLA target: {milestone.targetDays ?? 0} days
                        {milestone.warningDays !== null ? ` · Warning window: ${milestone.warningDays} days` : ''}
                      </p>
                    </div>
                    <HealthPill value={milestone.slaStatus} />
                  </div>

                  <div className="timeline-meta-grid">
                    <div>
                      <span className="helper-text">Actual</span>
                      <p>{formatDateTime(milestone.actualAt)}</p>
                    </div>
                    <div>
                      <span className="helper-text">Expected</span>
                      <p>{formatDateTime(milestone.expectedAt)}</p>
                    </div>
                  </div>

                  <div className="timeline-form-grid">
                    <label className="field">
                      <span>Timestamp</span>
                      <input
                        type="datetime-local"
                        value={draft.occurredAt}
                        disabled={locked || savingMilestone === milestone.milestoneType}
                        onChange={(event) =>
                          handleMilestoneDraftChange(
                            milestone.milestoneType,
                            'occurredAt',
                            event.target.value,
                          )
                        }
                      />
                    </label>

                    <label className="field field-full">
                      <span>Notes</span>
                      <textarea
                        rows={3}
                        value={draft.notes}
                        disabled={locked || savingMilestone === milestone.milestoneType}
                        onChange={(event) =>
                          handleMilestoneDraftChange(milestone.milestoneType, 'notes', event.target.value)
                        }
                      />
                    </label>
                  </div>

                  <div className="panel-actions">
                    <button
                      type="button"
                      className="primary-button"
                      disabled={locked || savingMilestone === milestone.milestoneType}
                      onClick={() => handleSaveMilestone(milestone.milestoneType)}
                    >
                      {savingMilestone === milestone.milestoneType
                        ? 'Saving...'
                        : milestone.completed
                          ? 'Update milestone'
                          : 'Record milestone'}
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        </section>

        <section className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Alerts</p>
              <h3>Current order alerts</h3>
            </div>
          </div>

          <div className="stack-list">
            {order.alerts.length > 0 ? (
              order.alerts.map((alert) => (
                <article key={alert.id} className={`alert-card ${alert.severity.toLowerCase()}`}>
                  <div className="list-title-row">
                    <strong>{alert.title}</strong>
                    <span className={`status-pill ${alert.severity === 'CRITICAL' ? 'danger' : 'warning'}`}>
                      {alert.severity === 'CRITICAL' ? 'Critical' : 'Warning'}
                    </span>
                  </div>
                  <p className="list-meta">
                    {alert.milestoneLabel} · Triggered {formatDateTime(alert.triggeredAt)}
                  </p>
                  <p>{alert.message}</p>
                </article>
              ))
            ) : (
              <p className="empty-state">No open alerts for this order.</p>
            )}
          </div>
        </section>
      </div>
    </section>
  );
}
