import { useState, type ChangeEvent, type DragEvent } from 'react';

interface BulkPdfImportProps {
  onUpload: (files: File[]) => Promise<void>;
  submitting: boolean;
}

function isPdf(file: File) {
  return file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
}

export function BulkPdfImport({ onUpload, submitting }: BulkPdfImportProps) {
  const [files, setFiles] = useState<File[]>([]);
  const [isDragging, setIsDragging] = useState(false);
  const [inputKey, setInputKey] = useState(0);

  function mergeFiles(nextFiles: File[]) {
    const pdfFiles = nextFiles.filter(isPdf);
    setFiles((currentFiles) => {
      const nextByName = new Map<string, File>();
      [...currentFiles, ...pdfFiles].forEach((file) => {
        nextByName.set(`${file.name}-${file.size}-${file.lastModified}`, file);
      });
      return Array.from(nextByName.values());
    });
  }

  function handleFileSelection(event: ChangeEvent<HTMLInputElement>) {
    mergeFiles(Array.from(event.target.files ?? []));
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setIsDragging(false);
    mergeFiles(Array.from(event.dataTransfer.files));
  }

  async function handleSubmit() {
    if (files.length === 0) {
      return;
    }

    await onUpload(files);
    setFiles([]);
    setInputKey((current) => current + 1);
  }

  function removeFile(fileToRemove: File) {
    setFiles((currentFiles) =>
      currentFiles.filter(
        (file) =>
          `${file.name}-${file.size}-${file.lastModified}` !==
          `${fileToRemove.name}-${fileToRemove.size}-${fileToRemove.lastModified}`,
      ),
    );
  }

  return (
    <section className="panel bulk-upload-card">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Bulk PDF Import</p>
          <h3>Drop multiple PDFs for automatic field extraction</h3>
        </div>
        <span className="helper-text">Text-based PDF parsing first. OCR is intentionally off.</span>
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
        <p>Drag PDF invoices here</p>
        <span>or select multiple files from disk</span>
        <label className="ghost-button file-picker">
          Choose PDFs
          <input
            key={inputKey}
            type="file"
            accept=".pdf,application/pdf"
            multiple
            onChange={handleFileSelection}
          />
        </label>
      </div>

      {files.length > 0 ? (
        <div className="selected-files">
          {files.map((file) => (
            <button
              key={`${file.name}-${file.size}-${file.lastModified}`}
              className="file-chip"
              type="button"
              onClick={() => removeFile(file)}
            >
              <span>{file.name}</span>
              <strong>Remove</strong>
            </button>
          ))}
        </div>
      ) : null}

      <div className="bulk-upload-actions">
        <span className="helper-text">{files.length} PDF files selected</span>
        <button
          className="primary-button"
          type="button"
          disabled={submitting || files.length === 0}
          onClick={() => void handleSubmit()}
        >
          {submitting ? 'Parsing PDFs...' : 'Import and extract'}
        </button>
      </div>
    </section>
  );
}

