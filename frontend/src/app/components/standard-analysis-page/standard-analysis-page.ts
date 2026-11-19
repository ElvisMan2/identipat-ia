import { ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';
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
export class StandardAnalysisPageComponent implements OnChanges, OnDestroy {
  private readonly document = inject(DOCUMENT);
  readonly interiorBannerUrl = `url('${new URL('banner-interior.png', this.document.baseURI).href}')`;

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
  @Input() pdfErrorMessage = '';
  @Input() selectedAudio: File | null = null;
  @Input() recordingAudio = false;
  @Input() recordingTimeLabel = '';
  @Input() analysisMessage = '';
  @Input() submittingAnalysis = false;
  @Input() canResumePendingAnalysis = false;

  pdfDragOver = false;
  curiosityFadeIn = false;
  readonly curiosities = [
    'La primera patente concedida en el Perú data de 1837 y fue otorgada a Carmen Noriega por una máquina para moler chocolate.',
    'El registro de diseño industrial permite proteger las características estéticas de los productos',
    'Entre 1911 y 1918, se concedieron 108 patentes de invención en el Perú.',
    'En Perú existen dos modalidades de patentamiento: patentes de invención y modelos de utilidad',
    'Además de ser nueva una invención debe cumplir con ventaja técnica o nivel inventivo para poder registrarse',
    'En Perú existe la protección acumulativa, por lo cual un producto puede estar protegido por más de un tipo de registro de propiedad intelectual.',
    'La fórmula de Coca-Cola es un ejemplo clásico de secreto empresarial, protegido durante más de 130 años.',
    'La Ley de Privilegios de Invención de 1869 fue el primer marco legal peruano en materia de patentes.',
    'La Decisión 486 estableció un régimen común de propiedad industrial para los países de la Comunidad Andina.',
    'El Certificado de Obtentor protege variedades vegetales dentro del sistema de propiedad intelectual.',
    'Las patentes son territoriales, es decir que tienen vigencia en el territorio del país donde se otorgan',
    'Un secreto empresarial puede protegerse indefinidamente mientras se mantenga confidencial.'
  ];
  currentCuriosity = this.curiosities[0];
  private pdfDragDepth = 0;
  private curiosityTimer: ReturnType<typeof setInterval> | null = null;
  private readonly changeDetector = inject(ChangeDetectorRef);

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['submittingAnalysis']) return;

    if (this.submittingAnalysis) {
      this.clearCuriosityTimer();
      this.showRandomCuriosity();
      this.curiosityTimer = setInterval(() => this.showRandomCuriosity(), 6_000);
    } else {
      this.clearCuriosityTimer();
    }
  }

  ngOnDestroy(): void {
    this.clearCuriosityTimer();
  }

  private showRandomCuriosity(): void {
    const options = this.curiosities.filter(curiosity => curiosity !== this.currentCuriosity);
    this.curiosityFadeIn = false;
    this.currentCuriosity = options[Math.floor(Math.random() * options.length)];
    this.changeDetector.detectChanges();
    requestAnimationFrame(() => {
      this.curiosityFadeIn = true;
      this.changeDetector.detectChanges();
    });
  }

  private clearCuriosityTimer(): void {
    if (this.curiosityTimer !== null) {
      clearInterval(this.curiosityTimer);
      this.curiosityTimer = null;
    }
  }

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
  @Output() dismissPdfTooLarge = new EventEmitter<void>();
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