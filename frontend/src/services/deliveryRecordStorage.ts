import * as XLSX from 'xlsx';
import type { DeliveryRecord, DeliveryRecordCreatePayload } from '../types/delivery';

const STORAGE_KEY = 'delivery-ocr-records';

function canUseLocalStorage() {
  return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined';
}

export function loadStoredDeliveryRecords(): DeliveryRecord[] {
  if (!canUseLocalStorage()) {
    return [];
  }

  try {
    const serializedRecords = window.localStorage.getItem(STORAGE_KEY);
    if (!serializedRecords) {
      return [];
    }

    const parsedRecords = JSON.parse(serializedRecords);
    return Array.isArray(parsedRecords) ? (parsedRecords as DeliveryRecord[]) : [];
  } catch {
    return [];
  }
}

export function saveStoredDeliveryRecord(payload: DeliveryRecordCreatePayload): DeliveryRecord {
  const now = new Date().toISOString();
  const record: DeliveryRecord = {
    id: Date.now(),
    itemName: payload.itemName,
    quantity: payload.quantity,
    date: payload.date,
    location: payload.location,
    poNumber: payload.poNumber,
    entryNote: payload.entryNote,
    rawText: payload.rawText,
    originalFileName: payload.originalFileName,
    createdAt: now,
    updatedAt: now,
  };

  const existingRecords = loadStoredDeliveryRecords();
  persistRecords([record, ...existingRecords]);
  return record;
}

export function exportStoredDeliveryRecords(records: DeliveryRecord[]) {
  const rows = records.map((record) => ({
    'Original File Name': record.originalFileName,
    'Item Name': record.itemName ?? '',
    Quantity: record.quantity ?? '',
    Date: record.date ?? '',
    Location: record.location ?? '',
    'PO Number': record.poNumber ?? '',
    'Entry Note': record.entryNote ?? '',
    'Raw Text': record.rawText ?? '',
    'Created At': record.createdAt,
    'Updated At': record.updatedAt,
  }));

  const workbook = XLSX.utils.book_new();
  const worksheet = XLSX.utils.json_to_sheet(rows);
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Delivery OCR');

  const workbookBytes = XLSX.write(workbook, { bookType: 'xlsx', type: 'array' });
  return {
    blob: new Blob([workbookBytes], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    }),
    fileName: 'delivery-records-export.xlsx',
  };
}

function persistRecords(records: DeliveryRecord[]) {
  if (!canUseLocalStorage()) {
    return;
  }

  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(records));
}
