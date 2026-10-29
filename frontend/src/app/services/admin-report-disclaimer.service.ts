import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ReportDisclaimer {
  id: string;
  version: number;
  title: string;
  content: string;
  status: string;
  activatedAt: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class AdminReportDisclaimerService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${environment.apiBaseUrl}/admin/report-disclaimers`;

  getAll(accessToken: string): Observable<ReportDisclaimer[]> {
    return this.http.get<ReportDisclaimer[]>(this.endpoint, {
      headers: { Authorization: `Bearer ${accessToken}` }
    });
  }

  createDraft(accessToken: string, title: string, content: string): Observable<ReportDisclaimer> {
    return this.http.post<ReportDisclaimer>(this.endpoint, { title, content }, {
      headers: { Authorization: `Bearer ${accessToken}` }
    });
  }

  activate(accessToken: string, disclaimerId: string): Observable<ReportDisclaimer> {
    return this.http.post<ReportDisclaimer>(
      `${this.endpoint}/${encodeURIComponent(disclaimerId)}/activate`,
      null,
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );
  }
}