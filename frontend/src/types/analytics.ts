export interface AnalyticsWindow {
  start: string | null;
  end: string | null;
}

export interface AnalyticsMetrics {
  totalInvoiceAmount: number;
  averageProductionDurationDays: number | null;
  averageShippingDurationDays: number | null;
  averageCustomsDurationDays: number | null;
  delayedOrderCount: number;
  atRiskOrderCount: number;
  onTimeDeliveryRatePercent: number | null;
}

export interface VendorInvoiceCount {
  vendor: string;
  invoiceCount: number;
}

export interface BottleneckStageDistribution {
  stage: string;
  count: number;
  sharePercent: number;
}

export interface PandasProcessingSample {
  description: string;
  rows: Record<string, unknown>[];
}

export interface AnalyticsReport {
  reportType: string;
  generatedAt: string;
  window: AnalyticsWindow;
  metrics: AnalyticsMetrics;
  invoiceCountByVendor: VendorInvoiceCount[];
  bottleneckStageDistribution: BottleneckStageDistribution[];
  pandasProcessingSample: PandasProcessingSample;
}
