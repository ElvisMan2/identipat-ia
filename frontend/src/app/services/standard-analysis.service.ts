import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of, switchMap } from 'rxjs';
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

  getAnalysis(analysisId: string, accessToken = ''): Observable<AnalysisResponse> {
    return this.http.get<AnalysisResponse>(
      `${this.endpoint}/analyses/${analysisId}`,
      accessToken
        ? { headers: { Authorization: `Bearer ${accessToken}` } }
        : { withCredentials: true }
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
