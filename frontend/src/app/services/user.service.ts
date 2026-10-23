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
  private accessToken = '';

  identify(doi: string, doiType: string): Observable<DocumentRecognitionResponse> {
    return this.http.post<DocumentRecognitionResponse>(`${this.endpoint}/identify`, { doi, doiType });
  }

  login(doi: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.endpoint}/login`, { doi, password });
  }

  setAccessToken(accessToken: string): void {
    this.accessToken = accessToken;
  }

  clearAccessToken(): void {
    this.accessToken = '';
  }

  private get authenticatedOptions(): { headers: { Authorization: string } } {
    return { headers: { Authorization: `Bearer ${this.accessToken}` } };
  }

  findAll(page = 0, size = 20): Observable<PagedResponse<User>> {
    return this.http.get<PagedResponse<User>>(this.endpoint, {
      ...this.authenticatedOptions,
      params: { page, size }
    });
  }

  exportAll(): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.endpoint}/export`, {
      ...this.authenticatedOptions,
      observe: 'response',
      responseType: 'blob'
    });
  }

  findById(userId: number): Observable<User> {
    return this.http.get<User>(`${this.endpoint}/${userId}`, this.authenticatedOptions);
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
