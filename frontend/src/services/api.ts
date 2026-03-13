import type {
  BatchUploadResult,
  DashboardSummary,
  Invoice,
  InvoiceFilters,
  InvoiceReviewUpdatePayload,
  InvoiceStatus,
  InvoiceUploadPayload,
} from '../types/invoice';
import type {
  AlertStatus,
  MilestoneRecordPayload,
  MilestoneType,
  SlaRule,
  SlaRuleUpdatePayload,
  SupplyChainAlert,
  SupplyChainDashboard,
  SupplyChainOrderDetail,
  SupplyChainOrderFilters,
  SupplyChainOrderPayload,
  SupplyChainOrderSummary,
} from '../types/supplyChain';
import type { AnalyticsReport } from '../types/analytics';
import type { AiInsight, AiOrderAnalysis } from '../types/ai';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api').replace(
  /\/$/,
  '',
);
const AI_API_BASE_URL = (import.meta.env.VITE_AI_API_BASE_URL ?? 'http://127.0.0.1:8001').replace(
  /\/$/,
  '',
);
const ANALYTICS_API_BASE_URL = (
  import.meta.env.VITE_ANALYTICS_API_BASE_URL ?? 'http://127.0.0.1:5001'
).replace(/\/$/, '');

interface ApiError {
  message?: string;
}

interface DownloadResponse {
  blob: Blob;
  fileName: string;
}

interface RawAnalyticsReport {
  report_type: string;
  generated_at: string;
  window: {
    start: string | null;
    end: string | null;
  };
  metrics: {
    total_invoice_amount: number;
    average_production_duration_days: number | null;
    average_shipping_duration_days: number | null;
    average_customs_duration_days: number | null;
    delayed_order_count: number;
    at_risk_order_count: number;
    on_time_delivery_rate_percent: number | null;
  };
  invoice_count_by_vendor: Array<{
    vendor: string;
    invoice_count: number;
  }>;
  bottleneck_stage_distribution: Array<{
    stage: string;
    count: number;
    share_percent: number;
  }>;
  pandas_processing_sample: {
    description: string;
    rows: Record<string, unknown>[];
  };
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}${path}`, init);
  } catch {
    throw new Error(
      `Cannot connect to the backend API at ${API_BASE_URL}. Make sure PostgreSQL and Spring Boot are running.`,
    );
  }

  if (!response.ok) {
    let message = 'Something went wrong while calling the API.';

    try {
      const data = (await response.json()) as ApiError;
      if (data.message) {
        message = data.message;
      }
    } catch {
      message = response.statusText || message;
    }

    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

async function requestAi<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;

  try {
    response = await fetch(`${AI_API_BASE_URL}${path}`, init);
  } catch {
    throw new Error(
      `Cannot connect to the AI service at ${AI_API_BASE_URL}. Make sure ai-service is running.`,
    );
  }

  if (!response.ok) {
    let message = 'Something went wrong while calling the AI service.';

    try {
      const data = (await response.json()) as ApiError & { detail?: string };
      if (data.detail) {
        message = data.detail;
      } else if (data.message) {
        message = data.message;
      }
    } catch {
      message = response.statusText || message;
    }

    throw new Error(message);
  }

  return (await response.json()) as T;
}

async function requestAnalytics<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;

  try {
    response = await fetch(`${ANALYTICS_API_BASE_URL}${path}`, init);
  } catch {
    throw new Error(
      `Cannot connect to the analytics service at ${ANALYTICS_API_BASE_URL}. Make sure analytics-service is running.`,
    );
  }

  if (!response.ok) {
    let message = 'Something went wrong while calling the analytics service.';

    try {
      const data = (await response.json()) as ApiError & { detail?: string };
      if (data.detail) {
        message = data.detail;
      } else if (data.message) {
        message = data.message;
      }
    } catch {
      message = response.statusText || message;
    }

    throw new Error(message);
  }

  return (await response.json()) as T;
}

function mapAnalyticsReport(report: RawAnalyticsReport): AnalyticsReport {
  return {
    reportType: report.report_type,
    generatedAt: report.generated_at,
    window: {
      start: report.window.start,
      end: report.window.end,
    },
    metrics: {
      totalInvoiceAmount: report.metrics.total_invoice_amount,
      averageProductionDurationDays: report.metrics.average_production_duration_days,
      averageShippingDurationDays: report.metrics.average_shipping_duration_days,
      averageCustomsDurationDays: report.metrics.average_customs_duration_days,
      delayedOrderCount: report.metrics.delayed_order_count,
      atRiskOrderCount: report.metrics.at_risk_order_count,
      onTimeDeliveryRatePercent: report.metrics.on_time_delivery_rate_percent,
    },
    invoiceCountByVendor: report.invoice_count_by_vendor.map((item) => ({
      vendor: item.vendor,
      invoiceCount: item.invoice_count,
    })),
    bottleneckStageDistribution: report.bottleneck_stage_distribution.map((item) => ({
      stage: item.stage,
      count: item.count,
      sharePercent: item.share_percent,
    })),
    pandasProcessingSample: report.pandas_processing_sample,
  };
}

async function requestBlob(path: string): Promise<DownloadResponse> {
  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}${path}`);
  } catch {
    throw new Error(
      `Cannot connect to the backend API at ${API_BASE_URL}. Make sure PostgreSQL and Spring Boot are running.`,
    );
  }

  if (!response.ok) {
    let message = 'Something went wrong while calling the API.';

    try {
      const data = (await response.json()) as ApiError;
      if (data.message) {
        message = data.message;
      }
    } catch {
      message = response.statusText || message;
    }

    throw new Error(message);
  }

  const contentDisposition = response.headers.get('content-disposition') ?? '';
  const fileNameMatch = contentDisposition.match(/filename="?([^"]+)"?/i);

  return {
    blob: await response.blob(),
    fileName: fileNameMatch?.[1] ?? 'invoices-export.xlsx',
  };
}

export function getDashboardSummary() {
  return request<DashboardSummary>('/dashboard');
}

export function getInvoices(filters: InvoiceFilters) {
  const params = new URLSearchParams();

  if (filters.vendor.trim()) {
    params.set('vendor', filters.vendor.trim());
  }

  if (filters.status) {
    params.set('status', filters.status);
  }

  const queryString = params.toString();
  return request<Invoice[]>(`/invoices${queryString ? `?${queryString}` : ''}`);
}

export function uploadInvoice(payload: InvoiceUploadPayload) {
  const formData = new FormData();
  formData.append('vendor', payload.vendor);
  formData.append('invoiceNumber', payload.invoiceNumber);
  formData.append('amount', payload.amount);
  formData.append('invoiceDate', payload.invoiceDate);
  formData.append('status', payload.status);
  formData.append('file', payload.file);

  return request<Invoice>('/invoices', {
    method: 'POST',
    body: formData,
  });
}

export function bulkUploadInvoices(files: File[]) {
  const formData = new FormData();

  files.forEach((file) => {
    formData.append('files', file);
  });

  return request<BatchUploadResult>('/invoices/bulk-upload', {
    method: 'POST',
    body: formData,
  });
}

export function saveReviewedInvoice(id: number, payload: InvoiceReviewUpdatePayload) {
  return request<Invoice>(`/invoices/${id}/review`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });
}

export function deleteInvoices(ids: number[]) {
  const params = new URLSearchParams();
  ids.forEach((id) => params.append('ids', String(id)));

  return request<void>(`/invoices?${params.toString()}`, {
    method: 'DELETE',
  });
}

export function exportInvoices(filters: InvoiceFilters) {
  const params = new URLSearchParams();

  if (filters.vendor.trim()) {
    params.set('vendor', filters.vendor.trim());
  }

  if (filters.status) {
    params.set('status', filters.status);
  }

  return requestBlob(`/invoices/export${params.toString() ? `?${params.toString()}` : ''}`);
}

export function updateInvoiceStatus(id: number, status: InvoiceStatus) {
  return request<Invoice>(`/invoices/${id}/status`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ status }),
  });
}

export function getSupplyChainDashboard() {
  return request<SupplyChainDashboard>('/supply-chain/dashboard');
}

export function getSupplyChainOrders(filters: SupplyChainOrderFilters) {
  const params = new URLSearchParams();

  if (filters.search.trim()) {
    params.set('search', filters.search.trim());
  }

  if (filters.healthStatus) {
    params.set('healthStatus', filters.healthStatus);
  }

  return request<SupplyChainOrderSummary[]>(
    `/supply-chain/orders${params.toString() ? `?${params.toString()}` : ''}`,
  );
}

export function getSupplyChainOrder(id: number) {
  return request<SupplyChainOrderDetail>(`/supply-chain/orders/${id}`);
}

export function createSupplyChainOrder(payload: SupplyChainOrderPayload) {
  return request<SupplyChainOrderDetail>('/supply-chain/orders', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });
}

export function updateSupplyChainOrder(id: number, payload: SupplyChainOrderPayload) {
  return request<SupplyChainOrderDetail>(`/supply-chain/orders/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });
}

export function recordSupplyChainMilestone(
  orderId: number,
  milestoneType: MilestoneType,
  payload: MilestoneRecordPayload,
) {
  return request<SupplyChainOrderDetail>(`/supply-chain/orders/${orderId}/milestones/${milestoneType}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });
}

export function deleteSupplyChainOrder(orderId: number) {
  return request<void>(`/supply-chain/orders/${orderId}`, {
    method: 'DELETE',
  });
}

export function getSupplyChainAlerts(status: AlertStatus | '') {
  const params = new URLSearchParams();
  if (status) {
    params.set('status', status);
  }

  return request<SupplyChainAlert[]>(
    `/supply-chain/alerts${params.toString() ? `?${params.toString()}` : ''}`,
  );
}

export function getSlaRules() {
  return request<SlaRule[]>('/supply-chain/sla-rules');
}

export function updateSlaRule(id: number, payload: SlaRuleUpdatePayload) {
  return request<SlaRule>(`/supply-chain/sla-rules/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });
}

export function getAiDailySummary() {
  return requestAi<AiInsight>('/ai/daily-summary', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({}),
  });
}

export function getAiWeeklySummary() {
  return requestAi<AiInsight>('/ai/weekly-summary', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({}),
  });
}

export function getAiRecommendations(scope: 'daily' | 'weekly' | 'summary' = 'daily') {
  return requestAi<AiInsight>('/ai/recommendations', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ scope }),
  });
}

export function getAiOrderAnalysis(orderId: number) {
  return requestAi<AiOrderAnalysis>('/ai/analyze-order', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ orderId }),
  });
}

export async function getAnalyticsDailyReport() {
  const report = await requestAnalytics<RawAnalyticsReport>('/reports/daily');
  return mapAnalyticsReport(report);
}

export async function getAnalyticsWeeklyReport() {
  const report = await requestAnalytics<RawAnalyticsReport>('/reports/weekly');
  return mapAnalyticsReport(report);
}

export async function getAnalyticsSummaryReport() {
  const report = await requestAnalytics<RawAnalyticsReport>('/reports/summary');
  return mapAnalyticsReport(report);
}
