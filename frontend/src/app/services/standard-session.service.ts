import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, of, switchMap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { CsrfService } from './csrf.service';

export interface StandardSessionResponse {
  status: string;
  expiresAt: string;
  absoluteExpiresAt: string;
  consentRequired: boolean;
}

export interface ConsentResponse {
  contentHash: string;
  decision: 'ACCEPTED' | 'REJECTED';
  acceptedAt: string | null;
  sessionStatus: string;
  consentRequired: boolean;
}

@Injectable({ providedIn: 'root' })
export class StandardSessionService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);
  private readonly endpoint = environment.apiBaseUrl;

  createSession(doi: string, doiType: string): Observable<StandardSessionResponse> {
    return this.csrf.ensureToken().pipe(
      switchMap(() => this.http.post<StandardSessionResponse>(
        `${this.endpoint}/standard-sessions`,
        { doi, doiType },
        { headers: this.csrf.headers(), withCredentials: true }
      ))
    );
  }

  getSession(): Observable<StandardSessionResponse> {
    return this.http.get<StandardSessionResponse>(`${this.endpoint}/standard-session`, {
      withCredentials: true
    });
  }

  accept(contentHash: string): Observable<ConsentResponse> {
    return this.decide(contentHash, 'ACCEPTED');
  }

  reject(contentHash: string): Observable<ConsentResponse> {
    return this.decide(contentHash, 'REJECTED');
  }

  closeSession(): Observable<void> {
    return this.csrf.ensureToken().pipe(
      switchMap(() => this.http.delete<void>(`${this.endpoint}/standard-session`, {
        headers: this.csrf.headers(),
        withCredentials: true
      })),
      catchError((error: unknown) => error instanceof HttpErrorResponse && error.status === 401
        ? of(void 0)
        : throwError(() => error))
    );
  }

  private decide(
    contentHash: string,
    decision: 'ACCEPTED' | 'REJECTED'
  ): Observable<ConsentResponse> {
    return this.csrf.ensureToken().pipe(
      switchMap(() => this.http.post<ConsentResponse>(
        `${this.endpoint}/standard-session/consent`,
        { contentHash, decision },
        { headers: this.csrf.headers(), withCredentials: true }
      ))
    );
  }
}
