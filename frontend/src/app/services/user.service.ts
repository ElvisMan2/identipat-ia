import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, switchMap } from 'rxjs';
import { User } from '../models/user.model';
import { environment } from '../../environments/environment';
import { CsrfService } from './csrf.service';

export interface DocumentRecognitionResponse {
  registered: boolean;
  passwordRequired: boolean;
  active?: boolean;
}

export interface LoginResponse {
  tokenType: string;
  accessToken: string;
}

export interface StandardUserRegistrationRequest {
  firstName: string;
  paternalLastName: string;
  maternalLastName: string;
  doi: string;
  doiType: string;
  birthDate: string;
  gender: string;
  email: string;
  mobilePhone: string;
  profession: string;
  legalAcceptance: {
    contentHash: string;
    accepted: true;
  };
}

export interface StandardUserRegistrationResponse {
  registered: boolean;
}

type UserRequest = User;

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);
  private readonly endpoint = `${environment.apiBaseUrl}/users`;
  private readonly adminTokenKey = 'identipat.admin.accessToken';
  private readonly adminDoiKey = 'identipat.admin.doi';
  private readonly adminDoiTypeKey = 'identipat.admin.doiType';
  private accessToken = '';

  constructor() {
    this.accessToken = this.sessionStorage()?.getItem(this.adminTokenKey) ?? '';
  }

  identify(doi: string, doiType: string): Observable<DocumentRecognitionResponse> {
    return this.http.post<DocumentRecognitionResponse>(`${this.endpoint}/identify`, { doi, doiType });
  }

  login(doi: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.endpoint}/login`, { doi, password });
  }

  setAccessToken(accessToken: string, doi: string, doiType: string): void {
    this.accessToken = accessToken;
    const storage = this.sessionStorage();
    storage?.setItem(this.adminTokenKey, accessToken);
    storage?.setItem(this.adminDoiKey, doi);
    storage?.setItem(this.adminDoiTypeKey, doiType);
  }

  clearAccessToken(): void {
    this.accessToken = '';
    const storage = this.sessionStorage();
    storage?.removeItem(this.adminTokenKey);
    storage?.removeItem(this.adminDoiKey);
    storage?.removeItem(this.adminDoiTypeKey);
  }

  getAccessToken(): string {
    return this.accessToken;
  }

  getAdminIdentity(): { doi: string; doiType: string } | null {
    const storage = this.sessionStorage();
    const doi = storage?.getItem(this.adminDoiKey);
    const doiType = storage?.getItem(this.adminDoiTypeKey);
    return this.accessToken && doi && doiType ? { doi, doiType } : null;
  }

  private sessionStorage(): Storage | null {
    if (typeof window === 'undefined') {
      return null;
    }

    try {
      return window.sessionStorage;
    } catch {
      return null;
    }
  }

  private get authenticatedOptions(): { headers: { Authorization: string } } {
    return { headers: { Authorization: `Bearer ${this.accessToken}` } };
  }

  findAll(page = 0, size = 20, search = '', status = '', userType = ''): Observable<PagedResponse<User>> {
    const params: Record<string, string | number> = { page, size, sort: 'userId,asc' };
    if (search.trim()) {
      params['search'] = search.trim();
    }
    if (status) {
      params['status'] = status;
    }
    if (userType) {
      params['userType'] = userType;
    }

    return this.http.get<PagedResponse<User>>(this.endpoint, {
      ...this.authenticatedOptions,
      params
    });
  }

  exportAll(search = '', status = '', userType = ''): Observable<HttpResponse<Blob>> {
    const params: Record<string, string> = {};
    if (search.trim()) {
      params['search'] = search.trim();
    }
    if (status) {
      params['status'] = status;
    }
    if (userType) {
      params['userType'] = userType;
    }

    return this.http.get(`${this.endpoint}/export`, {
      ...this.authenticatedOptions,
      params,
      observe: 'response',
      responseType: 'blob'
    });
  }

  findById(userId: number): Observable<User> {
    return this.http.get<User>(`${this.endpoint}/${userId}`, this.authenticatedOptions);
  }

  findByDoi(doi: string): Observable<User> {
    return this.http.get<User>(`${this.endpoint}/doi/${encodeURIComponent(doi)}`, this.authenticatedOptions);
  }

  create(user: StandardUserRegistrationRequest): Observable<StandardUserRegistrationResponse> {
    return this.csrf.ensureToken().pipe(
      switchMap(() => this.http.post<StandardUserRegistrationResponse>(this.endpoint, this.withoutPhone(user), {
        headers: this.csrf.headers(),
        withCredentials: true
      }))
    );
  }

  update(userId: number, user: UserRequest): Observable<User> {
    return this.http.put<User>(`${this.endpoint}/admin/${userId}`, this.withoutPhone(user), this.authenticatedOptions);
  }

  delete(userId: number): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${userId}`, this.authenticatedOptions);
  }

  updateProfile(userId: number, user: UserRequest): Observable<User> {
    return this.http.put<User>(`${this.endpoint}/${userId}`, this.withoutPhone(user), this.authenticatedOptions);
  }

  private withoutPhone<T extends object>(payload: T): Omit<T, 'phone'> {
    const { phone, ...requestBody } = payload as T & { phone?: unknown };
    void phone;
    return requestBody as Omit<T, 'phone'>;
  }
}
