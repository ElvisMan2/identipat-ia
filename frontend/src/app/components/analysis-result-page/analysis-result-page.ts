import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TrustFooter } from '../trust-footer/trust-footer';
import {
  AnalysisGuidanceResponse,
  AnalysisResponse,
  DiagnosticReport
} from '../../services/standard-analysis.service';

export interface OpenNextStepsRequest {
  modality: string;
  protectionType: string | null | undefined;
  guidance: AnalysisGuidanceResponse['modalities'][number] | null;
}

@Component({
  selector: 'app-analysis-result-page',
  standalone: true,
  imports: [TrustFooter],
  templateUrl: './analysis-result-page.html',
  styleUrl: './analysis-result-page.css'
})
export class AnalysisResultPageComponent {
  @Input() analysis: AnalysisResponse | null = null;
  @Input() report: DiagnosticReport | null = null;
  @Input() guidance: AnalysisGuidanceResponse | null = null;
  @Input() navigationPending: 'admin' | 'predictions' | 'exit' | null = null;
  @Input() exitError = '';
  @Input() reportLoadError = '';
  @Input() downloadingPdf = false;
  @Input() reportPdfError = '';
  @Input() nextStepsModal: { modality: string; contentHtml: string | null } | null = null;

  @Output() downloadPdf = new EventEmitter<void>();
  @Output() exit = new EventEmitter<void>();
  @Output() newAnalysis = new EventEmitter<void>();
  @Output() openNextSteps = new EventEmitter<OpenNextStepsRequest>();
  @Output() closeNextSteps = new EventEmitter<void>();

  reportField(value: string | null | undefined): string {
    return value?.trim() || 'campo faltante';
  }

  hasPatentScreeningModality(options: DiagnosticReport['protectionOptions']): boolean {
    return options?.some(option =>
      option.type === 'INVENTION_PATENT' || option.type === 'UTILITY_MODEL'
    ) ?? false;
  }

  screeningAssessmentLabel(
    article: string | null | undefined,
    assessmentLabel: string | null | undefined,
    assessment: string | null | undefined
  ): string {
    if (assessment === 'POTENTIAL_MATCH') {
      const labels: Record<string, string> = {
        'Artículo 15': 'Exclusiones a la patentabilidad',
        'Artículo 20': 'Excepciones a la patentabilidad'
      };
      if (article && labels[article]) {
        return labels[article];
      }
    }
    return assessmentLabel?.trim() || assessment?.trim() || 'campo faltante';
  }

  reportDateLabel(value: string | null | undefined): string {
    if (!value) {
      return 'campo faltante';
    }

    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? 'campo faltante'
      : new Intl.DateTimeFormat('es-PE', {
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone: 'America/Lima'
      }).format(date);
  }

  guidanceFor(type: string): AnalysisGuidanceResponse['modalities'][number] | null {
    return this.guidance?.modalities.find(modality => modality.protectionType === type) ?? null;
  }

  applicabilityLabel(applicability: string | null | undefined): string {
    const labels: Record<string, string> = {
      POSSIBLE: 'Posible',
      LIKELY: 'Probable',
      UNLIKELY: 'Poco probable',
      NO_POTENTIAL_MATCH: 'Sin coincidencia potencial',
      INSUFFICIENT_INFORMATION: 'Información insuficiente'
    };
    return applicability ? labels[applicability] ?? applicability : 'campo faltante';
  }
}