export interface DeliveryImageExtractionResult {
  item_name: string | null;
  quantity: number | null;
  date: string | null;
  location: string | null;
  po_number: string | null;
  entry_note: string | null;
  raw_text: string;
}

export interface DeliveryRecordCreatePayload {
  itemName: string | null;
  quantity: number | null;
  date: string | null;
  location: string | null;
  poNumber: string | null;
  entryNote: string | null;
  rawText: string;
  originalFileName: string;
}

export interface DeliveryRecord {
  id: number;
  itemName: string | null;
  quantity: number | null;
  date: string | null;
  location: string | null;
  poNumber: string | null;
  entryNote: string | null;
  rawText: string | null;
  originalFileName: string;
  createdAt: string;
  updatedAt: string;
}
