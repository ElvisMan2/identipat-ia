import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { environment } from '../../environments/environment';
import { CsrfService } from './csrf.service';

describe('CsrfService', () => {
  let service: CsrfService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CsrfService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads one token, reuses it and creates the configured header', () => {
    service.ensureToken().subscribe();
    service.ensureToken().subscribe();

    const request = http.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`);
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBeTrue();
    request.flush({ token: 'csrf-token', headerName: 'X-CUSTOM-CSRF' });

    expect(service.headers().get('X-CUSTOM-CSRF')).toBe('csrf-token');
    service.ensureToken().subscribe(value => expect(value.token).toBe('csrf-token'));
    http.expectNone(`${environment.apiBaseUrl}/standard-session/csrf`);
  });

  it('clears the in-memory token', () => {
    service.ensureToken().subscribe();
    http.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({
      token: 'csrf-token', headerName: 'X-XSRF-TOKEN'
    });
    service.clear();
    expect(service.headers().has('X-XSRF-TOKEN')).toBeFalse();
  });
});
