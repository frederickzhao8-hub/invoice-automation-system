import type { MilestoneSlaStatus, OrderHealthStatus } from '../../types/supplyChain';

interface HealthPillProps {
  value: OrderHealthStatus | MilestoneSlaStatus;
}

const labelMap: Record<OrderHealthStatus | MilestoneSlaStatus, string> = {
  ON_TIME: 'On time',
  AT_RISK: 'At risk',
  DELAYED: 'Delayed',
  COMPLETED_ON_TIME: 'Completed on time',
  COMPLETED_LATE: 'Completed late',
  PENDING: 'Pending',
  OVERDUE: 'Overdue',
};

const classMap: Record<OrderHealthStatus | MilestoneSlaStatus, string> = {
  ON_TIME: 'good',
  AT_RISK: 'warning',
  DELAYED: 'danger',
  COMPLETED_ON_TIME: 'good',
  COMPLETED_LATE: 'danger',
  PENDING: 'neutral',
  OVERDUE: 'danger',
};

export function HealthPill({ value }: HealthPillProps) {
  return <span className={`status-pill ${classMap[value]}`}>{labelMap[value]}</span>;
}
