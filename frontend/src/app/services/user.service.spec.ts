import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { StandardUserRegistrationRequest, UserService } from './user.service';

describe('UserService', () => {
  let service: UserService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(UserService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates a STANDARD user with the exact legalAcceptance contract, CSRF and credentials', () => {
    const request: StandardUserRegistrationRequest = {
      firstName: 'Ana',
      paternalLastName: 'Pérez',
      maternalLastName: 'Rojas',
      doi: '12345678',
      doiType: 'DNI',
      birthDate: '01/01/1990',
      gender: 'FEMALE',
      email: 'ana@example.com',
      phone: '016543210',
      mobilePhone: '912345678',
      profession: 'Abogada',
      legalAcceptance: { contentHash: 'current-hash', accepted: true }
    };

    service.create(request).subscribe();

    const csrf = http.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`);
    expect(csrf.request.method).toBe('GET');
    expect(csrf.request.withCredentials).toBeTrue();
    csrf.flush({ token: 'csrf-token', headerName: 'X-XSRF-TOKEN' });

    const create = http.expectOne(`${environment.apiBaseUrl}/users`);
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(request);
    expect(create.request.body.termsAccepted).toBeUndefined();
    expect(create.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
    expect(create.request.withCredentials).toBeTrue();
    create.flush({ registered: true });
  });
});
