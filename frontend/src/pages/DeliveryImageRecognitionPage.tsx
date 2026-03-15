import { useEffect, useMemo, useState, type ChangeEvent, type DragEvent } from 'react';
import {
  exportDeliveryRecords,
  extractDeliveryImage,
  getDeliveryRecords,
  saveDeliveryRecord,
} from '../services/api';
import {
  exportStoredDeliveryRecords,
  loadStoredDeliveryRecords,
  saveStoredDeliveryRecord,
} from '../services/deliveryRecordStorage';
import type { DeliveryImageExtractionResult, DeliveryRecord } from '../types/delivery';
import { formatDateTime, formatNumber } from '../utils/formatters';

function isSupportedImage(file: File) {
  return (
    file.type === 'image/jpeg' ||
    file.type === 'image/png' ||
    file.name.toLowerCase().endsWith('.jpg') ||
    file.name.toLowerCase().endsWith('.jpeg') ||
    file.name.toLowerCase().endsWith('.png')
  );
}

function displayValue(value: string | number | null) {
  return value === null || value === '' ? 'Not found' : String(value);
}

function hasStructuredField(result: DeliveryImageExtractionResult) {
  return (
    result.item_name !== null ||
    result.quantity !== null ||
    result.date !== null ||
    result.location !== null ||
    result.po_number !== null ||
    result.entry_note !== null
  );
}

function downloadBlob(blob: Blob, fileName: string) {
  const link = document.createElement('a');
  const objectUrl = URL.createObjectURL(blob);

  link.href = objectUrl;
  link.download = fileName;
  link.click();

  URL.revokeObjectURL(objectUrl);
}

export function DeliveryImageRecognitionPage() {
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [isDragging, setIsDragging] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [loadingRecords, setLoadingRecords] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [result, setResult] = useState<DeliveryImageExtractionResult | null>(null);
  const [records, setRecords] = useState<DeliveryRecord[]>([]);
  const [storageMode, setStorageMode] = useState<'backend' | 'local'>('backend');
  const [batchProgress, setBatchProgress] = useState<{
    completed: number;
    total: number;
    currentFileName: string | null;
  } | null>(null);

  const previewUrl = useMemo(() => {
    if (selectedFiles.length === 0) {
      return null;
    }
    return URL.createObjectURL(selectedFiles[0]);
  }, [selectedFiles]);

  useEffect(() => {
    return () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  useEffect(() => {
    let active = true;

    async function loadRecords() {
      try {
        const nextRecords = await getDeliveryRecords();
        if (active) {
          setRecords(nextRecords);
          setStorageMode('backend');
        }
      } catch {
        if (active) {
          setRecords(loadStoredDeliveryRecords());
          setStorageMode('local');
          setSuccessMessage(
            'Backend delivery storage is unavailable. New OCR results will be kept in this browser until PostgreSQL is back.',
          );
        }
      } finally {
        if (active) {
          setLoadingRecords(false);
        }
      }
    }

    void loadRecords();

    return () => {
      active = false;
    };
  }, []);

  function updateSelectedFiles(files: FileList | File[] | null) {
    const incomingFiles = Array.from(files ?? []);
    if (incomingFiles.length === 0) {
      return;
    }

    const supportedFiles = incomingFiles.filter((file) => isSupportedImage(file));
    if (supportedFiles.length === 0) {
      setErrorMessage('Only jpg, jpeg, and png images are supported.');
      return;
    }

    if (supportedFiles.length < incomingFiles.length) {
      setErrorMessage('Some files were ignored because only jpg, jpeg, and png images are supported.');
    } else {
      setErrorMessage(null);
    }

    setSelectedFiles(supportedFiles);
    setResult(null);
    setSuccessMessage(null);
    setBatchProgress(null);
  }

  function handleFileSelection(event: ChangeEvent<HTMLInputElement>) {
    updateSelectedFiles(event.target.files);
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setIsDragging(false);
    updateSelectedFiles(event.dataTransfer.files);
  }

  async function handleExtract() {
    if (selectedFiles.length === 0) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setSuccessMessage(null);
    setBatchProgress({
      completed: 0,
      total: selectedFiles.length,
      currentFileName: selectedFiles[0]?.name ?? null,
    });

    try {
      const savedRecords: DeliveryRecord[] = [];
      let succeededCount = 0;
      let failedCount = 0;
      let noDataCount = 0;
      let resolvedStorageMode: 'backend' | 'local' = storageMode;

      for (const [index, selectedFile] of selectedFiles.entries()) {
        setBatchProgress({
          completed: index,
          total: selectedFiles.length,
          currentFileName: selectedFile.name,
        });

        try {
          const nextResult = await extractDeliveryImage(selectedFile);
          setResult(nextResult);

          if (!hasStructuredField(nextResult)) {
            noDataCount += 1;
            continue;
          }

          const payload = {
            itemName: nextResult.item_name,
            quantity: nextResult.quantity,
            date: nextResult.date,
            location: nextResult.location,
            poNumber: nextResult.po_number,
            entryNote: nextResult.entry_note,
            rawText: nextResult.raw_text,
            originalFileName: selectedFile.name,
          };

          if (resolvedStorageMode === 'backend') {
            try {
              const savedRecord = await saveDeliveryRecord(payload);
              savedRecords.push(savedRecord);
              succeededCount += 1;
              continue;
            } catch {
              resolvedStorageMode = 'local';
              setStorageMode('local');
            }
          }

          const savedRecord = saveStoredDeliveryRecord(payload);
          savedRecords.push(savedRecord);
          succeededCount += 1;
        } catch {
          failedCount += 1;
        }
      }

      if (savedRecords.length > 0) {
        setRecords((currentRecords) => [...savedRecords.reverse(), ...currentRecords]);
      }

      setStorageMode(resolvedStorageMode);
      setSuccessMessage(
        `Processed ${selectedFiles.length} images: ${succeededCount} saved, ${noDataCount} with no structured fields, ${failedCount} failed. Storage mode: ${resolvedStorageMode}.`,
      );
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : 'Unable to extract delivery image fields.',
      );
    } finally {
      setBatchProgress((currentProgress) =>
        currentProgress
          ? {
              completed: currentProgress.total,
              total: currentProgress.total,
              currentFileName: null,
            }
          : null,
      );
      setSubmitting(false);
    }
  }

  async function handleExport() {
    setExporting(true);
    setErrorMessage(null);

    try {
      const exportResult =
        storageMode === 'local'
          ? exportStoredDeliveryRecords(records)
          : await exportDeliveryRecords();
      const { blob, fileName } = exportResult;
      downloadBlob(blob, fileName);
    } catch {
      const { blob, fileName } = exportStoredDeliveryRecords(records);
      downloadBlob(blob, fileName);
      setStorageMode('local');
      setSuccessMessage(
        'Backend export is unavailable. Exported the delivery OCR records currently stored in this browser instead.',
      );
    } finally {
      setExporting(false);
    }
  }

  return (
    <section className="module-stack">
      <section className="hero module-hero">
        <div>
          <p className="eyebrow">Delivery OCR</p>
          <h2>Extract logistics fields from delivery photos.</h2>
          <p className="hero-copy">
            Upload a delivery image and extract location, date, PO number, entry note, item name,
            quantity, and raw OCR text without using a fixed warehouse list.
          </p>
        </div>

        <div className="hero-badge">
          <span>OCR + Parsing</span>
          <strong>JPG, JPEG, PNG input with dynamic location extraction</strong>
        </div>
      </section>

      {errorMessage ? <div className="error-banner">{errorMessage}</div> : null}
      {successMessage ? <div className="success-banner">{successMessage}</div> : null}

      <div className="content-grid delivery-page-grid">
        <section className="panel delivery-upload-card">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Image Upload</p>
              <h3>Drop a delivery photo for OCR</h3>
            </div>
            <span className="helper-text">Supports jpg / jpeg / png</span>
          </div>

          <div
            className={`dropzone ${isDragging ? 'dragging' : ''}`}
            onDragEnter={(event) => {
              event.preventDefault();
              setIsDragging(true);
            }}
            onDragOver={(event) => {
              event.preventDefault();
              setIsDragging(true);
            }}
            onDragLeave={(event) => {
              event.preventDefault();
              setIsDragging(false);
            }}
            onDrop={handleDrop}
          >
            <p>Drag a delivery image here</p>
            <span>or select one image from disk</span>
            <label className="ghost-button file-picker">
              Choose Image
              <input
                type="file"
                multiple
                accept=".jpg,.jpeg,.png,image/jpeg,image/png"
                onChange={handleFileSelection}
              />
            </label>
          </div>

          {selectedFiles.length > 0 ? (
            <div className="selected-files">
              {selectedFiles.map((file) => (
                <div key={file.name + file.lastModified} className="file-chip">
                  <strong>{file.name}</strong>
                  <span>{Math.round(file.size / 1024)} KB</span>
                </div>
              ))}
            </div>
          ) : null}

          <div className="bulk-upload-actions">
            <span className="helper-text">
              {selectedFiles.length > 0
                ? `${selectedFiles.length} delivery image${selectedFiles.length === 1 ? '' : 's'} selected`
                : 'No delivery image selected'}
            </span>
            <button
              className="primary-button"
              type="button"
              disabled={submitting || selectedFiles.length === 0}
              onClick={() => void handleExtract()}
            >
              {submitting ? 'Running OCR...' : 'Extract And Save Batch'}
            </button>
          </div>

          {batchProgress ? (
            <div className="helper-text delivery-batch-progress">
              {batchProgress.completed >= batchProgress.total
                ? `Batch complete: ${batchProgress.total}/${batchProgress.total}`
                : `Processing ${batchProgress.completed + 1}/${batchProgress.total}: ${batchProgress.currentFileName}`}
            </div>
          ) : null}
        </section>

        <section className="panel image-preview-card">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Preview</p>
              <h3>{selectedFiles.length > 1 ? 'First selected delivery image' : 'Selected delivery image'}</h3>
            </div>
          </div>

          {previewUrl ? (
            <img className="image-preview" src={previewUrl} alt="Delivery upload preview" />
          ) : (
            <div className="empty-state image-empty-state">
              Select an image to preview it before OCR extraction.
            </div>
          )}
        </section>
      </div>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Structured Result</p>
            <h3>Unified logistics fields</h3>
          </div>
          <span className="helper-text">Missing values return null-equivalent UI placeholders</span>
        </div>

        <div className="delivery-result-grid">
          <article className="result-card">
            <span className="helper-text">Item Name</span>
            <strong>{displayValue(result?.item_name ?? null)}</strong>
          </article>
          <article className="result-card">
            <span className="helper-text">Quantity</span>
            <strong>{displayValue(result?.quantity ?? null)}</strong>
          </article>
          <article className="result-card">
            <span className="helper-text">Date</span>
            <strong>{displayValue(result?.date ?? null)}</strong>
          </article>
          <article className="result-card">
            <span className="helper-text">Location</span>
            <strong>{displayValue(result?.location ?? null)}</strong>
          </article>
          <article className="result-card">
            <span className="helper-text">PO Number</span>
            <strong>{displayValue(result?.po_number ?? null)}</strong>
          </article>
          <article className="result-card">
            <span className="helper-text">Entry Note</span>
            <strong>{displayValue(result?.entry_note ?? null)}</strong>
          </article>
        </div>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Raw OCR Text</p>
            <h3>OCR output retained for downstream review</h3>
          </div>
        </div>
        <pre className="ocr-raw-text">{result?.raw_text || 'No OCR output yet.'}</pre>
      </section>

      <section className="panel table-card">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Logistics Queue</p>
            <h3>Saved delivery OCR records</h3>
          </div>
          <div className="table-actions">
            <span className="helper-text">
              {records.length} saved records · {storageMode === 'backend' ? 'backend' : 'local browser'} mode
            </span>
            <button
              className="ghost-button"
              type="button"
              disabled={exporting || records.length === 0}
              onClick={() => void handleExport()}
            >
              {exporting ? 'Exporting...' : 'Export Excel'}
            </button>
          </div>
        </div>

        {loadingRecords ? (
          <div className="empty-state">Loading saved delivery records...</div>
        ) : records.length === 0 ? (
          <div className="empty-state">
            Extract a delivery image and it will appear here automatically.
          </div>
        ) : (
          <div className="table-wrapper">
            <table className="delivery-records-table">
              <thead>
                <tr>
                  <th>File</th>
                  <th>Item</th>
                  <th>Quantity</th>
                  <th>Date</th>
                  <th>Location</th>
                  <th>PO Number</th>
                  <th>Entry Note</th>
                  <th>Saved At</th>
                  <th>Raw Text</th>
                </tr>
              </thead>
              <tbody>
                {records.map((record) => (
                  <tr key={record.id}>
                    <td>{record.originalFileName}</td>
                    <td>{displayValue(record.itemName)}</td>
                    <td>{record.quantity === null ? 'Not found' : formatNumber(record.quantity)}</td>
                    <td>{displayValue(record.date)}</td>
                    <td>{displayValue(record.location)}</td>
                    <td>{displayValue(record.poNumber)}</td>
                    <td>{displayValue(record.entryNote)}</td>
                    <td>{formatDateTime(record.createdAt)}</td>
                    <td>
                      <details className="raw-preview">
                        <summary>Preview</summary>
                        <pre>{record.rawText || 'No OCR text saved.'}</pre>
                      </details>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </section>
  );
}
