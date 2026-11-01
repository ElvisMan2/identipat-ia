import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, switchMap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';

interface CsrfResponse {
  token: string;
  headerName: string;
}

interface StandardSessionResponse {
  status: string;
  expiresAt: string;
  absoluteExpiresAt: string;
  consentRequired: boolean;
  requiredConsentVersion: string;
}

export interface AnalysisCreatedResponse {
  analysisId: string;
  status: string;
  createdAt: string;
}

export interface AnalysisResult {
  schemaVersion: string;
  summary: string;
  protectionOptions: Array<{
    type: string;
    applicability: string;
    protectedSubjectMatter: string;
    rationale: string;
    legalBasis: Array<{
      instrument: string;
      articles: string[];
    }>;
  }>;
  patentScreening: {
    applicable: boolean;
    article15: LegalScreeningAssessment;
    article20: LegalScreeningAssessment;
  };
  geneticResourceAccess: {
    assessment: string;
    rationale: string;
    missingInformation: string[];
  };
  observations: string[];
  warnings: string[];
}

export interface LegalScreeningAssessment {
  assessment: string;
  rationale: string;
  matches: unknown[];
}

export interface AnalysisResponse {
  analysisId: string;
  inputType: string;
  status: string;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  failedAt: string | null;
  result: AnalysisResult | null;
  failure: { code: string; message: string } | null;
}

export interface DiagnosticReport {
  reportVersion: string | null;
  analysisId: string | null;
  sourceResultVersion: string | null;
  inputType: string | null;
  analysisCompletedAt: string | null;
  title: string | null;
  summary: string | null;
  applicabilityNote: string | null;
  protectionOptions: DiagnosticProtectionOption[] | null;
  patentScreening: DiagnosticPatentScreening | null;
  geneticResourceAccess: DiagnosticGeneticResourceAccess | null;
  historicalPatentability: {
    outcome: string;
    rationale: string;
  } | null;
  observations: string[] | null;
  warnings: string[] | null;
  scope: string | null;
  disclaimer: {
    id: string | null;
    version: number | null;
    title: string | null;
    content: string | null;
  } | null;
}

export interface DiagnosticProtectionOption {
  type: string | null;
  name: string | null;
  relationshipQuestion: string | null;
  applicability: string | null;
  applicabilityLabel: string | null;
  protectedSubjectMatter: string | null;
  rationale: string | null;
  legalBasis: Array<{
    instrument: string | null;
    articles: string[] | null;
  }> | null;
  orientation: string | null;
  patentScreening: DiagnosticPatentScreening | null;
  geneticResourceAccess: DiagnosticGeneticResourceAccess | null;
}

export interface DiagnosticPatentScreening {
  title: string | null;
  context: string | null;
  article15: DiagnosticScreeningArticle | null;
  article20: DiagnosticScreeningArticle | null;
  scopeNote: string | null;
}

export interface DiagnosticScreeningArticle {
  article: string | null;
  assessment: string | null;
  assessmentLabel: string | null;
  rationale: string | null;
  matches: Array<{
    literal: string | null;
    rationale: string | null;
  }> | null;
}

export interface DiagnosticGeneticResourceAccess {
  placement: string | null;
  assessment: string | null;
  assessmentLabel: string | null;
  rationale: string | null;
  missingInformation: string[] | null;
}

export interface AnalysisGuidanceResponse {
  guidanceVersion: string;
  analysisId: string;
  resolvedAt: string;
  modalities: Array<{
    protectionType: string;
    name: string;
    applicability: string;
    applicabilityLabel: string;
    guideVersion: number;
    shortDescription: string;
    nextSteps: { contentHtml: string } | null;
    lastVerifiedAt: string;
  }>;
}

@Injectable({
  providedIn: 'root'
})
export class StandardAnalysisService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = environment.apiBaseUrl;

  createTextAnalysis(doi: string, doiType: string, description: string, accessToken = ''): Observable<AnalysisCreatedResponse> {
    if (accessToken) {
      return this.http.post<AnalysisCreatedResponse>(
        `${this.endpoint}/analyses/text`,
        { description },
        { headers: { Authorization: `Bearer ${accessToken}` } }
      );
    }

    return this.http.get<CsrfResponse>(`${this.endpoint}/standard-session/csrf`, {
      withCredentials: true
    }).pipe(
      switchMap((csrf) => this.http.post<StandardSessionResponse>(
        `${this.endpoint}/standard-sessions`,
        { doi, doiType },
        { headers: this.csrfHeaders(csrf), withCredentials: true }
      )),
      switchMap((session) => {
        return this.http.post<unknown>(
          `${this.endpoint}/standard-session/consent`,
          { consentVersion: session.requiredConsentVersion, decision: 'ACCEPTED' },
          { headers: this.csrfHeadersFromToken(), withCredentials: true }
        ).pipe(switchMap(() => of(session)));
      }),
      switchMap(() => this.http.post<AnalysisCreatedResponse>(
        `${this.endpoint}/analyses/text`,
        { description },
        { headers: this.csrfHeadersFromToken(), withCredentials: true }
      ))
    );
  }

  createPdfAnalysis(doi: string, doiType: string, file: File, accessToken = ''): Observable<AnalysisCreatedResponse> {
    const body = new FormData();
    body.append('file', file, file.name);

    if (accessToken) {
      return this.http.post<AnalysisCreatedResponse>(
        `${this.endpoint}/analyses/pdf`,
        body,
        { headers: { Authorization: `Bearer ${accessToken}` } }
      );
    }

    return this.http.get<CsrfResponse>(`${this.endpoint}/standard-session/csrf`, {
      withCredentials: true
    }).pipe(
      switchMap((csrf) => this.http.post<StandardSessionResponse>(
        `${this.endpoint}/standard-sessions`,
        { doi, doiType },
        { headers: this.csrfHeaders(csrf), withCredentials: true }
      )),
      switchMap((session) => this.http.post<unknown>(
        `${this.endpoint}/standard-session/consent`,
        { consentVersion: session.requiredConsentVersion, decision: 'ACCEPTED' },
        { headers: this.csrfHeadersFromToken(), withCredentials: true }
      ).pipe(switchMap(() => of(session)))),
      switchMap(() => this.http.post<AnalysisCreatedResponse>(
        `${this.endpoint}/analyses/pdf`,
        body,
        { headers: this.csrfHeadersFromToken(), withCredentials: true }
      ))
    );
  }

  createAudioAnalysis(doi: string, doiType: string, file: File, accessToken = ''): Observable<AnalysisCreatedResponse> {
    const body = new FormData();
    body.append('file', file, file.name);

    if (accessToken) {
      return this.http.post<AnalysisCreatedResponse>(
        `${this.endpoint}/analyses/audio`,
        body,
        { headers: { Authorization: `Bearer ${accessToken}` } }
      );
    }

    return this.http.get<CsrfResponse>(`${this.endpoint}/standard-session/csrf`, {
      withCredentials: true
    }).pipe(
      switchMap((csrf) => this.http.post<StandardSessionResponse>(
        `${this.endpoint}/standard-sessions`,
        { doi, doiType },
        { headers: this.csrfHeaders(csrf), withCredentials: true }
      )),
      switchMap((session) => this.http.post<unknown>(
        `${this.endpoint}/standard-session/consent`,
        { consentVersion: session.requiredConsentVersion, decision: 'ACCEPTED' },
        { headers: this.csrfHeadersFromToken(), withCredentials: true }
      ).pipe(switchMap(() => of(session)))),
      switchMap(() => this.http.post<AnalysisCreatedResponse>(
        `${this.endpoint}/analyses/audio`,
        body,
        { headers: this.csrfHeadersFromToken(), withCredentials: true }
      ))
    );
  }

  getAnalysis(analysisId: string, accessToken = ''): Observable<AnalysisResponse> {
    return this.http.get<AnalysisResponse>(
      `${this.endpoint}/analyses/${analysisId}`,
      accessToken
        ? { headers: { Authorization: `Bearer ${accessToken}` } }
        : { withCredentials: true }
    );
  }

  getReport(analysisId: string, accessToken = ''): Observable<DiagnosticReport> {
    return this.http.get<DiagnosticReport>(
      `${this.endpoint}/analyses/${encodeURIComponent(analysisId)}/report`,
      accessToken
        ? { headers: { Authorization: `Bearer ${accessToken}` } }
        : { withCredentials: true }
    );
  }

  getGuidance(analysisId: string, accessToken = ''): Observable<AnalysisGuidanceResponse> {
    return this.http.get<AnalysisGuidanceResponse>(
      `${this.endpoint}/analyses/${encodeURIComponent(analysisId)}/guidance`,
      accessToken
        ? { headers: { Authorization: `Bearer ${accessToken}` } }
        : { withCredentials: true }
    );
  }

  getReportPdf(analysisId: string, accessToken = ''): Observable<Blob> {
    const url = `${this.endpoint}/analyses/${encodeURIComponent(analysisId)}/report.pdf`;
    return this.http.get(url, accessToken
      ? { headers: { Authorization: `Bearer ${accessToken}` }, responseType: 'blob' }
      : { withCredentials: true, responseType: 'blob' }
    );
  }

  closeStandardSession(): Observable<void> {
    return this.http.get<CsrfResponse>(`${this.endpoint}/standard-session/csrf`, {
      withCredentials: true
    }).pipe(
      switchMap((csrf) => this.http.delete<void>(`${this.endpoint}/standard-session`, {
        headers: { [csrf.headerName]: csrf.token },
        withCredentials: true
      }).pipe(catchError((error: unknown) =>
        error instanceof HttpErrorResponse && error.status === 401
          ? of(void 0)
          : throwError(() => error)
      )))
    );
  }

  private csrfToken = '';

  private csrfHeaders(csrf: CsrfResponse): HttpHeaders {
    this.csrfToken = csrf.token;
    return new HttpHeaders({ [csrf.headerName]: csrf.token });
  }

  private csrfHeadersFromToken(): HttpHeaders {
    return new HttpHeaders({ 'X-XSRF-TOKEN': this.csrfToken });
  }
}
