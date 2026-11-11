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
  @Output() submitAnalysis = new EventEmitter<void>();
  @Output() stopRecording = new EventEmitter<void>();
}