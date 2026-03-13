import { useEffect, useState } from 'react';
import {
  getAiDailySummary,
  getAiRecommendations,
  getAiWeeklySummary,
  getAnalyticsDailyReport,
  getAnalyticsWeeklyReport,
} from '@frontend/services/api';
import type { AnalyticsReport } from '@frontend/types/analytics';
import type { AiInsight } from '@frontend/types/ai';
import {
  formatCurrency,
  formatDateTimeCompact,
  formatNumber,
  formatStageLabel,
} from '@frontend/utils/formatters';

export function OperationsOverviewPage() {
  const [dailyReport, setDailyReport] = useState<AnalyticsReport | null>(null);
  const [weeklyReport, setWeeklyReport] = useState<AnalyticsReport | null>(null);
  const [dailyInsight, setDailyInsight] = useState<AiInsight | null>(null);
  const [weeklyInsight, setWeeklyInsight] = useState<AiInsight | null>(null);
  const [recommendations, setRecommendations] = useState<AiInsight | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadOverview() {
      setLoading(true);
      setErrorMessage(null);

      try {
        const [
          dailyReportResponse,
          weeklyReportResponse,
          dailyInsightResponse,
          weeklyInsightResponse,
          recommendationsResponse,
        ] = await Promise.all([
          getAnalyticsDailyReport(),
          getAnalyticsWeeklyReport(),
          getAiDailySummary(),
          getAiWeeklySummary(),
          getAiRecommendations('weekly'),
        ]);

        if (!cancelled) {
          setDailyReport(dailyReportResponse);
          setWeeklyReport(weeklyReportResponse);
          setDailyInsight(dailyInsightResponse);
          setWeeklyInsight(weeklyInsightResponse);
          setRecommendations(recommendationsResponse);
        }
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(
            error instanceof Error
              ? error.message
              : 'Unable to load the operations dashboard remote.',
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadOverview();

    return () => {
      cancelled = true;
    };
  }, []);

  const weeklyMetrics = weeklyReport?.metrics;
  const dailyMetrics = dailyReport?.metrics;
  const topBottleneck = weeklyReport?.bottleneckStageDistribution?.[0] ?? null;
  const topVendors = weeklyReport?.invoiceCountByVendor?.slice(0, 4) ?? [];

  return (
    <section className="module-stack">
      <section className="hero module-hero">
        <div>
          <p className="eyebrow">Operations Intelligence</p>
          <h2>Analytics and AI insight panels served from a dedicated remote application.</h2>
          <p className="hero-copy">
            This remote calls `analytics-service` and `ai-service` directly, then packages the
            resulting metrics and recommendations into a deployable operations dashboard.
          </p>
        </div>

        <div className="hero-badge">
          <span>Sources</span>
          <strong>analytics-service + ai-service</strong>
        </div>
      </section>

      {errorMessage ? <div className="error-banner">{errorMessage}</div> : null}

      <div className="mfe-stat-grid">
        <article className="stat-card accent-card">
          <span>Weekly Invoice Amount</span>
          <h2>{formatCurrency(weeklyMetrics?.totalInvoiceAmount ?? null)}</h2>
          <p className="helper-text">Current 7-day operational window.</p>
        </article>
        <article className="stat-card">
          <span>Delayed Orders</span>
          <h2>{weeklyMetrics?.delayedOrderCount ?? (loading ? '...' : 0)}</h2>
          <p className="helper-text">Open SLA breach count from analytics-service.</p>
        </article>
        <article className="stat-card">
          <span>At Risk</span>
          <h2>{weeklyMetrics?.atRiskOrderCount ?? (loading ? '...' : 0)}</h2>
          <p className="helper-text">Orders inside warning windows.</p>
        </article>
        <article className="stat-card">
          <span>Top Bottleneck</span>
          <h2>{topBottleneck ? formatStageLabel(topBottleneck.stage) : loading ? '...' : 'N/A'}</h2>
          <p className="helper-text">
            {topBottleneck ? `${formatNumber(topBottleneck.sharePercent)}% of weekly breaches` : 'No breaches in scope.'}
          </p>
        </article>
      </div>

      <div className="content-grid dashboard-grid">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Daily Analytics</p>
              <h3>Fresh operational signals</h3>
            </div>
          </div>

          <div className="mfe-highlight-list">
            <div>
              <strong>Window</strong>
              <span className="helper-text">
                {formatDateTimeCompact(dailyReport?.window.start ?? null)} to{' '}
                {formatDateTimeCompact(dailyReport?.window.end ?? null)}
              </span>
            </div>
            <div>
              <strong>Shipping Duration</strong>
              <span className="helper-text">
                {dailyMetrics?.averageShippingDurationDays !== null &&
                dailyMetrics?.averageShippingDurationDays !== undefined
                  ? `${formatNumber(dailyMetrics.averageShippingDurationDays)} day(s)`
                  : 'No complete shipping durations in the current daily window.'}
              </span>
            </div>
            <div>
              <strong>Daily Bottlenecks</strong>
              <ul className="mfe-list-reset">
                {(dailyReport?.bottleneckStageDistribution ?? []).length > 0 ? (
                  (dailyReport?.bottleneckStageDistribution ?? []).map((entry) => (
                    <li key={entry.stage}>
                      {formatStageLabel(entry.stage)} · {formatNumber(entry.count)} breach(es) ·{' '}
                      {formatNumber(entry.sharePercent)}%
                    </li>
                  ))
                ) : (
                  <li>No daily bottlenecks in scope.</li>
                )}
              </ul>
            </div>
          </div>
        </section>

        <section className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Weekly AI Summary</p>
              <h3>Grounded narrative</h3>
            </div>
          </div>

          {weeklyInsight ? (
            <>
              <p className="insight-narrative">{weeklyInsight.narrative}</p>
              <ul className="insight-list">
                {weeklyInsight.keyFindings.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </>
          ) : (
            <p className="empty-state">{loading ? 'Loading weekly AI summary...' : 'No summary available.'}</p>
          )}
        </section>
      </div>

      <div className="content-grid dashboard-grid">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Daily AI Insight</p>
              <h3>Short-form operational narrative</h3>
            </div>
          </div>

          {dailyInsight ? (
            <>
              <p className="insight-narrative">{dailyInsight.narrative}</p>
              <p className="helper-text">
                Bottleneck:{' '}
                {dailyInsight.mostCommonBottleneckStage
                  ? formatStageLabel(dailyInsight.mostCommonBottleneckStage)
                  : 'No active bottleneck'}
              </p>
            </>
          ) : (
            <p className="empty-state">{loading ? 'Loading daily AI insight...' : 'No insight available.'}</p>
          )}
        </section>

        <section className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Invoice Concentration</p>
              <h3>Top vendors in the weekly window</h3>
            </div>
          </div>

          <ul className="insight-list">
            {topVendors.length > 0 ? (
              topVendors.map((vendor) => (
                <li key={vendor.vendor}>
                  {vendor.vendor} · {formatNumber(vendor.invoiceCount)} invoice(s)
                </li>
              ))
            ) : (
              <li>No vendor concentration data in the current weekly window.</li>
            )}
          </ul>
        </section>
      </div>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Recommended Actions</p>
            <h3>Operations team playbook</h3>
          </div>
        </div>

        {recommendations ? (
          <div className="ai-insight-grid">
            <article className="insight-card">
              <span className="helper-text">Narrative</span>
              <p className="insight-narrative">{recommendations.narrative}</p>
            </article>

            <article className="insight-card">
              <span className="helper-text">Actions</span>
              <ul className="insight-list">
                {recommendations.recommendedActions.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </article>

            <article className="insight-card">
              <span className="helper-text">Grounded Sources</span>
              <ul className="insight-list">
                <li>Weekly delayed orders: {weeklyMetrics?.delayedOrderCount ?? 0}</li>
                <li>Weekly at-risk orders: {weeklyMetrics?.atRiskOrderCount ?? 0}</li>
                <li>
                  Dominant bottleneck:{' '}
                  {topBottleneck ? formatStageLabel(topBottleneck.stage) : 'No weekly bottleneck'}
                </li>
              </ul>
            </article>
          </div>
        ) : (
          <p className="empty-state">
            {loading ? 'Loading recommended actions...' : 'No recommendations available.'}
          </p>
        )}
      </section>
    </section>
  );
}
