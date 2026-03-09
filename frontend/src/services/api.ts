import type {
  BatchUploadResult,
  DashboardSummary,
  Invoice,
  InvoiceFilters,
  InvoiceReviewUpdatePayload,
  InvoiceStatus,
  InvoiceUploadPayload,
} from '../types/invoice';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api').replace(
  /\/$/,
  '',
);

interface ApiError {
  message?: string;
}

interface DownloadResponse {
  blob: Blob;
  fileName: string;
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
