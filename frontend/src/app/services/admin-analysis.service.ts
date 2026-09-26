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

@Injectable({
  providedIn: 'root'
})
export class AdminAnalysisService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${environment.apiBaseUrl}/admin/analyses`;
  private accessToken = '';

  setAccessToken(accessToken: string): void {
    this.accessToken = accessToken;
  }

  clearAccessToken(): void {
    this.accessToken = '';
  }

  findAll(): Observable<AdminAnalysis[]> {
    return this.http.get<AdminAnalysis[]>(this.endpoint, {
      headers: { Authorization: `Bearer ${this.accessToken}` }
    });
  }
}