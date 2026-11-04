import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type AnalysisStatus = 'RECEIVED' | 'PREPROCESSING' | 'ANALYZING' | 'COMPLETED' | 'FAILED';

export interface AdminAnalysis {
  analysisId: string;
  createdAt: string;
  status: AnalysisStatus;
  firstName: string;
  lastNames: string;
  doiType: string;
  doi: string;
  inputType: string;
  conclusion: string | null;
  identifiedModalities: string[];
  analysisOrigin: string;
  guidanceConsulted: boolean;
}

export interface AdminAnalysesPage {
  content: AdminAnalysis[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface DiagnosticReport {
  reportVersion: string;
  analysisId: string;
  sourceResultVersion: string;
  inputType: string;
  analysisCompletedAt: string;
  title: string;
  summary: string;
  applicabilityNote: string;
  protectionOptions: unknown[];
  patentScreening: unknown;
  geneticResourceAccess: unknown;
  historicalPatentability: unknown;
  observations: string[];
  warnings: string[];
  scope: string;
  disclaimer: {
    id: string;
    version: number;
    title: string;
    content: string;
  };
}

@Injectable({
  providedIn: 'root'
})
export class AdminAnalysisService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = environment.apiBaseUrl;
  private accessToken = '';

  setAccessToken(accessToken: string): void {
    this.accessToken = accessToken;
  }

  clearAccessToken(): void {
    this.accessToken = '';
  }

  findAll(
    page: number,
    size: number,
    search: string,
    from: string,
    to: string,
    accessToken = this.accessToken
  ): Observable<AdminAnalysesPage> {
    const params: Record<string, string | number> = { page, size, sort: 'createdAt,desc' };
    if (search.trim()) params['search'] = search.trim();
    if (from) params['from'] = from;
    if (to) params['to'] = to;

    return this.http.get<AdminAnalysesPage>(`${this.endpoint}/admin/analyses`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      params
    });
  }

  getReport(analysisId: string): Observable<DiagnosticReport> {
    return this.http.get<DiagnosticReport>(
      `${this.endpoint}/analyses/${encodeURIComponent(analysisId)}/report`, {
      headers: { Authorization: `Bearer ${this.accessToken}` }
    });
  }
}