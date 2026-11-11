import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { LegalDocument } from '../../services/legal-document.service';
import { TrustFooter } from '../trust-footer/trust-footer';

@Component({
  selector: 'app-standard-analysis-page',
  standalone: true,
  imports: [ReactiveFormsModule, TrustFooter],
  templateUrl: './standard-analysis-page.html',
  styleUrl: './standard-analysis-page.css'
})
export class StandardAnalysisPageComponent {
  @Input({ required: true }) analysisForm!: FormGroup;
  @Input() adminAuthenticated = false;
  @Input() navigationPending: 'admin' | 'predictions' | 'exit' | null = null;
  @Input() exitError = '';
  @Input() standardAccessState: 'UNIDENTIFIED' | 'IDENTIFYING' | 'SESSION_CREATING' | 'CONSENT_REQUIRED' | 'READY' | 'BLOCKED' = 'UNIDENTIFIED';
  @Input() legalLoading = false;
  @Input() legalError = '';
  @Input() legalDocument: LegalDocument | null = null;
  @Input() consentMessage = '';
  @Input() consentChecked = false;
  @Input() consentSaving = false;
  @Input() legalDateLabel = (_value: string | null | undefined): string => '';
  @Input() selectedPdf: File | null = null;
  @Input() selectedAudio: File | null = null;
  @Input() recordingAudio = false;
  @Input() recordingTimeLabel = '';
  @Input() analysisMessage = '';
  @Input() submittingAnalysis = false;
  @Input() canResumePendingAnalysis = false;

  pdfDragOver = false;
  private pdfDragDepth = 0;

  @Output() exit = new EventEmitter<void>();
  @Output() openAdminPanel = new EventEmitter<void>();
  @Output() reviewLegal = new EventEmitter<void>();
  @Output() rejectLegal = new EventEmitter<void>();
  @Output() acceptLegal = new EventEmitter<void>();
  @Output() consentCheckedChange = new EventEmitter<Event>();
  @Output() removePdf = new EventEmitter<void>();
  @Output() removeAudio = new EventEmitter<void>();
  @Output() toggleRecording = new EventEmitter<void>();
  @Output() pdfSelected = new EventEmitter<Event>();
  @Output() pdfDropped = new EventEmitter<File>();
  @Output() submitAnalysis = new EventEmitter<void>();
  @Output() stopRecording = new EventEmitter<void>();

  onPdfDragEnter(event: DragEvent): void {
    if (!event.dataTransfer?.types.includes('Files')) {
      return;
    }

    event.preventDefault();
    this.pdfDragDepth++;
    this.pdfDragOver = true;
  }

  onPdfDragOver(event: DragEvent): void {
    if (!event.dataTransfer?.types.includes('Files')) {
      return;
    }

    event.preventDefault();
    event.dataTransfer.dropEffect = 'copy';
  }

  onPdfDragLeave(event: DragEvent): void {
    if (!event.dataTransfer?.types.includes('Files')) {
      return;
    }

    event.preventDefault();
    this.pdfDragDepth = Math.max(0, this.pdfDragDepth - 1);
    this.pdfDragOver = this.pdfDragDepth > 0;
  }

  onPdfDrop(event: DragEvent): void {
    event.preventDefault();
    this.pdfDragDepth = 0;
    this.pdfDragOver = false;

    const file = event.dataTransfer?.files.item(0);
    if (file) {
      this.pdfDropped.emit(file);
    }
  }
}