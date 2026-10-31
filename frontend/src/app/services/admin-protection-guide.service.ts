import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ProtectionGuide {
  guideId: string;
  protectionType: string;
  version: number;
  shortDescription: string;
  nextStepsContentHtml: string;
  status: string;
}

@Injectable({
  providedIn: 'root'
})
export class AdminProtectionGuideService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${environment.apiBaseUrl}/admin/protection-guides`;

  getAll(accessToken: string): Observable<ProtectionGuide[]> {
    return this.http.get<ProtectionGuide[]>(this.endpoint, {
      headers: { Authorization: `Bearer ${accessToken}` }
    });
  }

  createDraft(accessToken: string, shortDescription: string, nextStepsContentHtml: string): Observable<ProtectionGuide> {
    return this.http.post<ProtectionGuide>(this.endpoint, {
      protectionType: 'INVENTION_PATENT',
      shortDescription,
      nextStepsContentHtml
    }, {
      headers: { Authorization: `Bearer ${accessToken}` }
    });
  }

  activate(accessToken: string, guideId: string): Observable<ProtectionGuide> {
    return this.http.post<ProtectionGuide>(
      `${this.endpoint}/${encodeURIComponent(guideId)}/activate`,
      null,
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );
  }
}
