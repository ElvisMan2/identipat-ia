import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface LegalDocument {
  documentId: string;
  title: string;
  contentHtml: string;
  contentHash: string;
  updatedAt: string;
}

export interface LegalDocumentUpdateRequest {
  title: string;
  contentHtml: string;
}

@Injectable({ providedIn: 'root' })
export class LegalDocumentService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = environment.apiBaseUrl;

  getCurrent(): Observable<LegalDocument> {
    return this.http.get<LegalDocument>(`${this.endpoint}/legal-document`);
  }

  getAdminCurrent(accessToken: string): Observable<LegalDocument> {
    return this.http.get<LegalDocument>(`${this.endpoint}/admin/legal-document`, {
      headers: { Authorization: `Bearer ${accessToken}` }
    });
  }

  updateAdmin(accessToken: string, request: LegalDocumentUpdateRequest): Observable<LegalDocument> {
    return this.http.put<LegalDocument>(`${this.endpoint}/admin/legal-document`, request, {
      headers: { Authorization: `Bearer ${accessToken}` }
    });
  }
}
