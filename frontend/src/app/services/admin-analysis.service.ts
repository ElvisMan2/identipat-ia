import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type AnalysisStatus = 'RECEIVED' | 'PREPROCESSING' | 'ANALYZING' | 'COMPLETED' | 'FAILED';

export interface AdminAnalysis {
  analysisId: string;
  userId: number;
  firstName: string;
  paternalLastName: string;
  maternalLastName: string;
  requestedAt: string;
  status: AnalysisStatus;
  summary: string | null;
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
  private readonly endpoint = `${environment.apiBaseUrl}/analyses`;
  private accessToken = '';

  setAccessToken(accessToken: string): void {
    this.accessToken = accessToken;
  }

  clearAccessToken(): void {
    this.accessToken = '';
  }

  getReport(analysisId: string): Observable<DiagnosticReport> {
    return this.http.get<DiagnosticReport>(
      `${this.endpoint}/${encodeURIComponent(analysisId)}/report`, {
      headers: { Authorization: `Bearer ${this.accessToken}` }
    });
  }
}