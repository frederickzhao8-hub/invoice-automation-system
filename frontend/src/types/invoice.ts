export type InvoiceStatus = 'PENDING' | 'APPROVED' | 'PAID';
export type InvoiceParseStatus = 'SUCCESS' | 'PARTIAL' | 'FAILED';
export type InvoiceProcessingStatus = 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'DUPLICATE';

export interface Invoice {
  id: number;
  vendor: string | null;
  vendorName: string | null;
  invoiceNumber: string | null;
  amount: number | null;
  totalAmount: number | null;
  quantity: number | null;
  unitPrice: number | null;
  subtotalAmount: number | null;
  taxAmount: number | null;
  currency: string | null;
  invoiceDate: string | null;
  issueDate: string | null;
  dueDate: string | null;
  paymentTerms: string | null;
  invoiceDescription: string | null;
  status: InvoiceStatus;
  parseStatus: InvoiceParseStatus;
  processingStatus: InvoiceProcessingStatus;
  parseConfidence: number | null;
  rawExtractedText: string | null;
  needsReview: boolean;
  duplicateFlag: boolean;
  duplicateReason: string | null;
  extractionError: string | null;
  originalFileName: string;
  createdAt: string;
  updatedAt: string;
}

export interface BatchUploadItemResult {
  fileName: string;
  status: InvoiceProcessingStatus;
  invoiceId: number | null;
  vendorName: string | null;
  invoiceNumber: string | null;
  duplicate: boolean;
  message: string;
}

export interface BatchUploadResult {
  totalFiles: number;
  successCount: number;
  duplicateCount: number;
  failureCount: number;
  results: BatchUploadItemResult[];
  savedInvoices: Invoice[];
}

export interface DashboardSummary {
  totalInvoices: number;
  totalAmount: number;
  pendingCount: number;
  approvedCount: number;
  paidCount: number;
}

export interface InvoiceFilters {
  vendor: string;
  status: '' | InvoiceStatus;
}

export interface InvoiceUploadPayload {
  vendor: string;
  invoiceNumber: string;
  amount: string;
  invoiceDate: string;
  status: InvoiceStatus;
  file: File;
}

export interface InvoiceReviewUpdatePayload {
  invoiceNumber: string | null;
  vendorName: string | null;
  quantity: number | null;
  unitPrice: number | null;
  subtotalAmount: number | null;
  taxAmount: number | null;
  totalAmount: number | null;
  currency: string | null;
  issueDate: string | null;
}

export interface ReviewInvoiceDraft {
  id: number;
  originalFileName: string;
  parseStatus: InvoiceParseStatus;
  parseConfidence: number | null;
  needsReview: boolean;
  rawExtractedText: string | null;
  invoiceNumber: string;
  vendorName: string;
  quantity: string;
  unitPrice: string;
  subtotalAmount: string;
  taxAmount: string;
  totalAmount: string;
  currency: string;
  issueDate: string;
}

export type ReviewInvoiceEditableField =
  | 'invoiceNumber'
  | 'vendorName'
  | 'quantity'
  | 'unitPrice'
  | 'subtotalAmount'
  | 'taxAmount'
  | 'totalAmount'
  | 'currency'
  | 'issueDate';
