import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, finalize, of, shareReplay, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface CsrfResponse {
  token: string;
  headerName: string;
}

@Injectable({ providedIn: 'root' })
export class CsrfService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${environment.apiBaseUrl}/standard-session/csrf`;
  private current: CsrfResponse | null = null;
  private inFlight: Observable<CsrfResponse> | null = null;

  ensureToken(): Observable<CsrfResponse> {
    if (this.current) {
      return of(this.current);
    }

    return this.inFlight ?? this.loadToken();
  }

  refreshToken(): Observable<CsrfResponse> {
    this.current = null;
    return this.inFlight ?? this.loadToken();
  }

  headers(): HttpHeaders {
    return this.current
      ? new HttpHeaders({ [this.current.headerName]: this.current.token })
      : new HttpHeaders();
  }

  clear(): void {
    this.current = null;
    this.inFlight = null;
  }

  private loadToken(): Observable<CsrfResponse> {
    const request = this.http.get<CsrfResponse>(this.endpoint, { withCredentials: true }).pipe(
      tap(response => this.current = response),
      finalize(() => this.inFlight = null),
      shareReplay({ bufferSize: 1, refCount: false })
    );
    this.inFlight = request;
    return request;
  }
}
