import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { environment } from '../../environments/environment';
import { StandardSessionService } from './standard-session.service';

describe('StandardSessionService', () => {
  let service: StandardSessionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(StandardSessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function flushCsrf(): void {
    http.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({
      token: 'csrf-token', headerName: 'X-XSRF-TOKEN'
    });
  }

  it('creates and reads a session with cookie credentials', () => {
    service.createSession('12345678', 'DNI').subscribe();
    flushCsrf();
    const create = http.expectOne(`${environment.apiBaseUrl}/standard-sessions`);
    expect(create.request.body).toEqual({ doi: '12345678', doiType: 'DNI' });
    expect(create.request.withCredentials).toBeTrue();
    expect(create.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
    create.flush({ status: 'ACTIVE', expiresAt: '', absoluteExpiresAt: '', consentRequired: false });

    service.getSession().subscribe();
    const get = http.expectOne(`${environment.apiBaseUrl}/standard-session`);
    expect(get.request.method).toBe('GET');
    expect(get.request.withCredentials).toBeTrue();
    get.flush({ status: 'ACTIVE', expiresAt: '', absoluteExpiresAt: '', consentRequired: false });
  });

  it('accepts, rejects and closes using contentHash without a legal version', () => {
    service.accept('hash-a').subscribe();
    flushCsrf();
    const accept = http.expectOne(`${environment.apiBaseUrl}/standard-session/consent`);
    expect(accept.request.body).toEqual({ contentHash: 'hash-a', decision: 'ACCEPTED' });
    accept.flush({ contentHash: 'hash-a', decision: 'ACCEPTED', acceptedAt: '', sessionStatus: 'ACTIVE', consentRequired: false });

    service.reject('hash-b').subscribe();
    const reject = http.expectOne(`${environment.apiBaseUrl}/standard-session/consent`);
    expect(reject.request.body).toEqual({ contentHash: 'hash-b', decision: 'REJECTED' });
    reject.flush({ contentHash: 'hash-b', decision: 'REJECTED', acceptedAt: null, sessionStatus: 'ACTIVE', consentRequired: true });

    service.closeSession().subscribe();
    const close = http.expectOne(`${environment.apiBaseUrl}/standard-session`);
    expect(close.request.method).toBe('DELETE');
    expect(close.request.withCredentials).toBeTrue();
    close.flush(null);
  });
});
