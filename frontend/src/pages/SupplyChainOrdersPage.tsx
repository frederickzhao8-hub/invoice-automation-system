import { startTransition, useDeferredValue, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { HealthPill } from '../components/supplyChain/HealthPill';
import {
  createSupplyChainOrder,
  deleteSupplyChainOrder,
  getSupplyChainOrders,
} from '../services/api';
import type { SupplyChainOrderFilters, SupplyChainOrderSummary } from '../types/supplyChain';
import { formatCurrency, formatDateTimeCompact, formatNumber } from '../utils/formatters';
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
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [refreshToken, setRefreshToken] = useState(0);
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
                <th>Value</th>
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
                    <td>{formatCurrency(order.orderValue)}</td>
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
                  <td colSpan={7}>
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
    </section>
  );
}
