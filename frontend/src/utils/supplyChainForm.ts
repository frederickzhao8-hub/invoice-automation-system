import type {
  SupplyChainOrderDetail,
  SupplyChainOrderPayload,
} from '../types/supplyChain';
import type { SupplyChainOrderFormValues } from '../components/supplyChain/OrderEditor';
import { toDateTimeLocalValue } from './formatters';

export function createEmptyOrderFormValues(): SupplyChainOrderFormValues {
  const now = new Date();
  const offset = now.getTimezoneOffset();
  const localNow = new Date(now.getTime() - offset * 60_000).toISOString().slice(0, 16);

  return {
    orderNumber: '',
    customerName: '',
    supplierName: '',
    productName: '',
    originCountry: '',
    destinationCountry: '',
    quantity: '',
    orderValue: '',
    notes: '',
    poReceivedAt: localNow,
  };
}

export function toOrderFormValues(order: SupplyChainOrderDetail): SupplyChainOrderFormValues {
  const poReceivedMilestone = order.timeline.find((milestone) => milestone.milestoneType === 'PO_RECEIVED');

  return {
    orderNumber: order.orderNumber,
    customerName: order.customerName,
    supplierName: order.supplierName,
    productName: order.productName,
    originCountry: order.originCountry,
    destinationCountry: order.destinationCountry,
    quantity: String(order.quantity),
    orderValue: String(order.orderValue),
    notes: order.notes ?? '',
    poReceivedAt: toDateTimeLocalValue(poReceivedMilestone?.actualAt),
  };
}

export function toOrderPayload(values: SupplyChainOrderFormValues): SupplyChainOrderPayload {
  return {
    orderNumber: values.orderNumber.trim(),
    customerName: values.customerName.trim(),
    supplierName: values.supplierName.trim(),
    productName: values.productName.trim(),
    originCountry: values.originCountry.trim(),
    destinationCountry: values.destinationCountry.trim(),
    quantity: Number(values.quantity),
    orderValue: Number(values.orderValue),
    notes: values.notes.trim() ? values.notes.trim() : null,
    poReceivedAt: values.poReceivedAt,
  };
}
