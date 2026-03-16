export type MilestoneType =
  | 'PO_RECEIVED'
  | 'PRODUCTION_COMPLETED'
  | 'SHIPPED'
  | 'ARRIVED_PORT'
  | 'CUSTOMS_CLEARED'
  | 'DELIVERED';

export type OrderHealthStatus = 'ON_TIME' | 'AT_RISK' | 'DELAYED';
export type AlertType = 'AT_RISK' | 'SLA_BREACH';
export type AlertSeverity = 'WARNING' | 'CRITICAL';
export type AlertStatus = 'OPEN' | 'RESOLVED';
export type MilestoneSlaStatus =
  | 'COMPLETED_ON_TIME'
  | 'COMPLETED_LATE'
  | 'PENDING'
  | 'AT_RISK'
  | 'OVERDUE';

export interface SupplyChainDashboard {
  totalOrders: number;
  onTimeOrders: number;
  atRiskOrders: number;
  delayedOrders: number;
  deliveredOrders: number;
  openAlerts: number;
}

export interface SupplyChainOrderSummary {
  id: number;
  orderNumber: string;
  customerName: string;
  supplierName: string;
  productName: string;
  originCountry: string;
  destinationCountry: string;
  quantity: number;
  orderValue: number;
  currentMilestone: MilestoneType | null;
  currentMilestoneLabel: string | null;
  nextMilestone: MilestoneType | null;
  nextMilestoneLabel: string | null;
  nextExpectedAt: string | null;
  healthStatus: OrderHealthStatus;
  completed: boolean;
  openAlertCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface OrderMilestone {
  milestoneType: MilestoneType;
  milestoneLabel: string;
  actualAt: string | null;
  expectedAt: string | null;
  targetDays: number | null;
  warningDays: number | null;
  slaStatus: MilestoneSlaStatus;
  completed: boolean;
  breached: boolean;
  atRisk: boolean;
  notes: string | null;
}

export interface SupplyChainAlert {
  id: number;
  orderId: number;
  orderNumber: string;
  customerName: string;
  milestoneType: MilestoneType;
  milestoneLabel: string;
  alertType: AlertType;
  severity: AlertSeverity;
  status: AlertStatus;
  title: string;
  message: string;
  expectedAt: string | null;
  triggeredAt: string;
  resolvedAt: string | null;
}

export interface SupplyChainOrderDetail {
  id: number;
  orderNumber: string;
  customerName: string;
  supplierName: string;
  productName: string;
  originCountry: string;
  destinationCountry: string;
  quantity: number;
  orderValue: number;
  notes: string | null;
  currentMilestone: MilestoneType | null;
  currentMilestoneLabel: string | null;
  nextMilestone: MilestoneType | null;
  nextMilestoneLabel: string | null;
  nextExpectedAt: string | null;
  healthStatus: OrderHealthStatus;
  completed: boolean;
  openAlertCount: number;
  timeline: OrderMilestone[];
  alerts: SupplyChainAlert[];
  createdAt: string;
  updatedAt: string;
}

export interface SlaRule {
  id: number;
  startMilestone: MilestoneType;
  startMilestoneLabel: string;
  endMilestone: MilestoneType;
  endMilestoneLabel: string;
  targetDays: number;
  warningDays: number;
  active: boolean;
  updatedAt: string;
}

export interface SupplyChainOrderPayload {
  orderNumber: string;
  customerName: string;
  supplierName: string;
  productName: string;
  originCountry: string;
  destinationCountry: string;
  quantity: number;
  orderValue: number;
  notes: string | null;
  poReceivedAt: string;
}

export interface MilestoneRecordPayload {
  occurredAt: string;
  notes: string | null;
}

export interface SlaRuleUpdatePayload {
  targetDays: number;
  warningDays: number;
  active: boolean;
}

export interface SupplyChainOrderFilters {
  search: string;
  healthStatus: '' | OrderHealthStatus;
}

export interface SupplyChainMilestoneImportItemResult {
  rowNumber: number;
  orderNumber: string | null;
  status: 'SUCCESS' | 'SKIPPED' | 'FAILED';
  updatedMilestones: string[];
  historyEntriesCreated: number;
  message: string;
}

export interface SupplyChainMilestoneImportResult {
  totalRows: number;
  successCount: number;
  skippedCount: number;
  failureCount: number;
  historyEntriesCreated: number;
  results: SupplyChainMilestoneImportItemResult[];
}

export interface MilestoneImportHistoryEntry {
  id: number;
  orderId: number;
  orderNumber: string;
  milestoneType: MilestoneType;
  milestoneLabel: string;
  previousOccurredAt: string | null;
  newOccurredAt: string;
  previousNotes: string | null;
  newNotes: string | null;
  sourceFileName: string;
  importedAt: string;
}
