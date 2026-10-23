import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User } from '../models/user.model';
import { environment } from '../../environments/environment';

export interface DocumentRecognitionResponse {
  registered: boolean;
  passwordRequired: boolean;
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
  phone: string;
  mobilePhone: string;
  profession: string;
}

export interface StandardUserRegistrationResponse {
  registered: boolean;
}

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

  findAll(page = 0, size = 20, search = ''): Observable<PagedResponse<User>> {
    return this.http.get<PagedResponse<User>>(this.endpoint, {
      ...this.authenticatedOptions,
      params: search.trim() ? { page, size, search: search.trim() } : { page, size }
    });
  }

  exportAll(search = ''): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.endpoint}/export`, {
      ...this.authenticatedOptions,
      params: search.trim() ? { search: search.trim() } : {},
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
    return this.http.post<StandardUserRegistrationResponse>(this.endpoint, user);
  }

  update(userId: number, user: User): Observable<User> {
    return this.http.put<User>(`${this.endpoint}/admin/${userId}`, user, this.authenticatedOptions);
  }

  updateProfile(userId: number, user: User): Observable<User> {
    return this.http.put<User>(`${this.endpoint}/${userId}`, user, this.authenticatedOptions);
  }
}
