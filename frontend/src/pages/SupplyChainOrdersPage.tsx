import { startTransition, useDeferredValue, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { HealthPill } from '../components/supplyChain/HealthPill';
import {
  createSupplyChainOrder,
  deleteSupplyChainOrder,
  getSupplyChainMilestoneImportHistory,
  getSupplyChainOrders,
  importSupplyChainMilestones,
} from '../services/api';
import type {
  MilestoneImportHistoryEntry,
  SupplyChainMilestoneImportResult,
  SupplyChainOrderFilters,
  SupplyChainOrderSummary,
} from '../types/supplyChain';
import { formatDateTime, formatDateTimeCompact, formatNumber } from '../utils/formatters';
import { OrderEditor, type SupplyChainOrderFormValues } from '../components/supplyChain/OrderEditor';
import { createEmptyOrderFormValues, toOrderPayload } from '../utils/supplyChainForm';

export function SupplyChainOrdersPage() {
  const navigate = useNavigate();
  const [filters, setFilters] = useState<SupplyChainOrderFilters>({ search: '', healthStatus: '' });
  const deferredSearch = useDeferredValue(filters.search);

  const [orders, setOrders] = useState<SupplyChainOrderSummary[]>([]);
  const [formValues, setFormValues] = useState<SupplyChainOrderFormValues>(createEmptyOrderFormValues);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [importingMilestones, setImportingMilestones] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [refreshToken, setRefreshToken] = useState(0);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [latestImportResult, setLatestImportResult] = useState<SupplyChainMilestoneImportResult | null>(null);
  const [importHistory, setImportHistory] = useState<MilestoneImportHistoryEntry[]>([]);
  const [loadingImportHistory, setLoadingImportHistory] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadOrders() {
      setLoading(true);
      setErrorMessage(null);

      try {
        const response = await getSupplyChainOrders({
          search: deferredSearch,
          healthStatus: filters.healthStatus,
        });
        if (!cancelled) {
          setOrders(response);
        }
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(error instanceof Error ? error.message : 'Unable to load orders.');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadOrders();

    return () => {
      cancelled = true;
    };
  }, [deferredSearch, filters.healthStatus, refreshToken]);

  useEffect(() => {
    let cancelled = false;

    async function loadImportHistory() {
      setLoadingImportHistory(true);

      try {
        const response = await getSupplyChainMilestoneImportHistory(12);
        if (!cancelled) {
          setImportHistory(response);
        }
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(
            error instanceof Error ? error.message : 'Unable to load milestone import history.',
          );
        }
      } finally {
        if (!cancelled) {
          setLoadingImportHistory(false);
        }
      }
    }

    void loadImportHistory();

    return () => {
      cancelled = true;
    };
  }, [refreshToken]);

  function handleFormChange(field: keyof SupplyChainOrderFormValues, value: string) {
    setFormValues((current) => ({ ...current, [field]: value }));
  }

  async function handleCreateOrder() {
    setSubmitting(true);
    setErrorMessage(null);

    try {
      const response = await createSupplyChainOrder(toOrderPayload(formValues));
      setFormValues(createEmptyOrderFormValues());
      navigate(`/supply-chain/orders/${response.id}`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to create order.');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDeleteOrder(orderId: number) {
    const confirmed = window.confirm('Delete this order and its milestone history?');
    if (!confirmed) {
      return;
    }

    setDeletingId(orderId);
    setErrorMessage(null);

    try {
      await deleteSupplyChainOrder(orderId);
      startTransition(() => {
        setRefreshToken((current) => current + 1);
      });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to delete order.');
    } finally {
      setDeletingId(null);
    }
  }

  async function handleImportMilestones() {
    if (!importFile) {
      return;
    }

    setImportingMilestones(true);
    setErrorMessage(null);

    try {
      const response = await importSupplyChainMilestones(importFile);
      setLatestImportResult(response);
      setImportFile(null);
      startTransition(() => {
        setRefreshToken((current) => current + 1);
      });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to import milestone workbook.');
    } finally {
      setImportingMilestones(false);
    }
  }

  return (
    <section className="module-stack">
      {errorMessage ? <div className="error-banner">{errorMessage}</div> : null}

      <div className="content-grid order-page-grid">
        <OrderEditor
          title="Create a tracked supply-chain order"
          description="New orders automatically start at PO Received and inherit the standard SLA rule set."
          submitLabel="Create order"
          values={formValues}
          submitting={submitting}
          onChange={handleFormChange}
          onSubmit={handleCreateOrder}
        />

        <section className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Filters</p>
              <h2>Find active orders</h2>
              <p className="hero-copy compact-copy">
                Filter by order number, customer, supplier, product, or health status.
              </p>
            </div>
          </div>

          <div className="filter-grid">
            <label className="field">
              <span>Search</span>
              <input
                value={filters.search}
                onChange={(event) =>
                  setFilters((current) => ({ ...current, search: event.target.value }))
                }
                placeholder="SC-1002, MetroNet, FiberHome..."
              />
            </label>

            <label className="field">
              <span>Health status</span>
              <select
                value={filters.healthStatus}
                onChange={(event) =>
                  setFilters((current) => ({
                    ...current,
                    healthStatus: event.target.value as SupplyChainOrderFilters['healthStatus'],
                  }))
                }
              >
                <option value="">All orders</option>
                <option value="ON_TIME">On time</option>
                <option value="AT_RISK">At risk</option>
                <option value="DELAYED">Delayed</option>
              </select>
            </label>
          </div>
        </section>
      </div>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Excel Import</p>
            <h3>Batch update milestone timestamps from Excel</h3>
            <p className="hero-copy compact-copy">
              Upload an Excel file with a first-column <strong>PO Number</strong> or{' '}
              <strong>Order Number</strong>, followed by milestone columns such as PO Received,
              Production Completed, Shipped, Arrived Port, Customs Cleared, and Delivered.
              Missing orders will be auto-created when the row also includes Customer, Supplier,
              Product, Origin Country, and Destination Country.
            </p>
          </div>
        </div>

        <div className="form-grid">
          <label className="field field-full">
            <span>Excel file</span>
            <input
              type="file"
              accept=".xlsx,.xls"
              onChange={(event) => setImportFile(event.target.files?.[0] ?? null)}
            />
          </label>
        </div>

        <p className="helper-text">
          Supported timestamp formats: Excel date cells, `YYYY-MM-DD HH:mm`, `YYYY/MM/DD HH:mm`,
          and common slash-based date-time formats. Rows are imported independently, so one bad row
          does not roll back the entire file.
        </p>
        <p className="helper-text">
          Recommended column order: <strong>PO Number</strong>, Customer, Supplier, Product, Origin
          Country, Destination Country, Quantity, Notes, PO Received, Production Completed,
          Shipped, Arrived Port, Customs Cleared, Delivered.
          <strong> Order Value is not required in this import flow.</strong>
        </p>

        <div className="panel-actions">
          <span className="helper-text">{importFile ? importFile.name : 'No Excel file selected'}</span>
          <button
            type="button"
            className="primary-button"
            disabled={!importFile || importingMilestones}
            onClick={() => void handleImportMilestones()}
          >
            {importingMilestones ? 'Importing...' : 'Import milestone workbook'}
          </button>
        </div>

        {latestImportResult ? (
          <div className="stack-list">
            <p className="helper-text">
              Processed {latestImportResult.totalRows} rows: {latestImportResult.successCount} succeeded,{' '}
              {latestImportResult.skippedCount} skipped, {latestImportResult.failureCount} failed.{' '}
              {latestImportResult.historyEntriesCreated} history records were captured.
            </p>

            <div className="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>Row</th>
                    <th>PO number</th>
                    <th>Status</th>
                    <th>Updated milestones</th>
                    <th>Message</th>
                  </tr>
                </thead>
                <tbody>
                  {latestImportResult.results.map((item) => (
                    <tr key={`${item.rowNumber}-${item.orderNumber ?? 'unknown'}`}>
                      <td>{item.rowNumber}</td>
                      <td>{item.orderNumber ?? 'Missing'}</td>
                      <td>{item.status}</td>
                      <td>{item.updatedMilestones.length > 0 ? item.updatedMilestones.join(', ') : 'None'}</td>
                      <td>{item.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ) : null}
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Order List</p>
            <h3>{loading ? 'Loading orders...' : `${orders.length} tracked orders`}</h3>
          </div>
        </div>

        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Order</th>
                <th>Flow</th>
                <th>Quantity</th>
                <th>Next due</th>
                <th>Alerts</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {orders.length > 0 ? (
                orders.map((order) => (
                  <tr key={order.id}>
                    <td>
                      <div className="invoice-primary-cell">
                        <strong>{order.orderNumber}</strong>
                        <span>{order.customerName}</span>
                        <span className="helper-text">{order.productName}</span>
                      </div>
                    </td>
                    <td>
                      <div className="review-meta">
                        <HealthPill value={order.healthStatus} />
                        <span className="helper-text">
                          {order.currentMilestoneLabel ?? 'Pending start'} →{' '}
                          {order.nextMilestoneLabel ?? 'Completed'}
                        </span>
                      </div>
                    </td>
                    <td>{formatNumber(order.quantity)}</td>
                    <td>{formatDateTimeCompact(order.nextExpectedAt)}</td>
                    <td>{order.openAlertCount}</td>
                    <td>
                      <div className="table-actions">
                        <Link to={`/supply-chain/orders/${order.id}`} className="ghost-button page-link">
                          View
                        </Link>
                        <button
                          type="button"
                          className="ghost-button danger-button"
                          disabled={deletingId === order.id}
                          onClick={() => handleDeleteOrder(order.id)}
                        >
                          {deletingId === order.id ? 'Deleting...' : 'Delete'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={6}>
                    <div className="empty-state">
                      {loading ? 'Loading tracked orders...' : 'No orders match the current filters.'}
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Import History</p>
            <h3>Recent milestone changes</h3>
            <p className="hero-copy compact-copy">
              Each imported milestone change is stored so historical timeline updates are not lost.
            </p>
          </div>
        </div>

        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Imported at</th>
                <th>PO number</th>
                <th>Milestone</th>
                <th>Previous timestamp</th>
                <th>New timestamp</th>
                <th>Source file</th>
              </tr>
            </thead>
            <tbody>
              {importHistory.length > 0 ? (
                importHistory.map((entry) => (
                  <tr key={entry.id}>
                    <td>{formatDateTimeCompact(entry.importedAt)}</td>
                    <td>{entry.orderNumber}</td>
                    <td>{entry.milestoneLabel}</td>
                    <td>{formatDateTime(entry.previousOccurredAt)}</td>
                    <td>{formatDateTime(entry.newOccurredAt)}</td>
                    <td>{entry.sourceFileName}</td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={6}>
                    <div className="empty-state">
                      {loadingImportHistory
                        ? 'Loading milestone import history...'
                        : 'No milestone import history has been recorded yet.'}
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </section>
  );
}
