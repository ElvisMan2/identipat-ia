import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { environment } from '../../environments/environment';
import { StandardAnalysisService } from './standard-analysis.service';

describe('StandardAnalysisService', () => {
  let service: StandardAnalysisService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(StandardAnalysisService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts each STANDARD input only to its analysis endpoint with CSRF and credentials', () => {
    service.createTextAnalysis('Descripción suficientemente extensa').subscribe();
    http.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({ token: 'csrf', headerName: 'X-XSRF-TOKEN' });
    const text = http.expectOne(`${environment.apiBaseUrl}/analyses/text`);
    expect(text.request.body).toEqual({ description: 'Descripción suficientemente extensa' });
    expect(text.request.withCredentials).toBeTrue();
    expect(text.request.headers.get('X-XSRF-TOKEN')).toBe('csrf');
    text.flush({ analysisId: '1', status: 'RECEIVED', createdAt: '' });

    service.createPdfAnalysis(new File(['pdf'], 'file.pdf', { type: 'application/pdf' })).subscribe();
    expect(http.expectOne(`${environment.apiBaseUrl}/analyses/pdf`).request.withCredentials).toBeTrue();

    service.createAudioAnalysis(new File(['audio'], 'file.webm', { type: 'audio/webm' })).subscribe();
    expect(http.expectOne(`${environment.apiBaseUrl}/analyses/audio`).request.withCredentials).toBeTrue();

    http.expectNone(`${environment.apiBaseUrl}/standard-sessions`);
    http.expectNone(`${environment.apiBaseUrl}/standard-session/consent`);
  });

  it('uses Bearer for ADMIN without STANDARD CSRF', () => {
    service.createTextAnalysis('Descripción suficientemente extensa', 'admin-token').subscribe();
    const request = http.expectOne(`${environment.apiBaseUrl}/analyses/text`);
    expect(request.request.headers.get('Authorization')).toBe('Bearer admin-token');
    expect(request.request.withCredentials).toBeFalse();
    http.expectNone(`${environment.apiBaseUrl}/standard-session/csrf`);
  });
});
