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
    const textKey = '7c4ee7a0-06c5-4b90-9572-8dc3ecf65d8f';
    service.createTextAnalysis('Descripción suficientemente extensa', textKey).subscribe();
    http.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({ token: 'csrf', headerName: 'X-XSRF-TOKEN' });
    const text = http.expectOne(`${environment.apiBaseUrl}/analyses/text`);
    expect(text.request.body).toEqual({ description: 'Descripción suficientemente extensa' });
    expect(text.request.withCredentials).toBeTrue();
    expect(text.request.headers.get('X-XSRF-TOKEN')).toBe('csrf');
    expect(text.request.headers.get('Idempotency-Key')).toBe(textKey);
    text.flush({ analysisId: '1', status: 'RECEIVED', createdAt: '' });

    const pdfKey = '9dde9328-d7a4-46ca-91de-952b0ed15ef8';
    service.createPdfAnalysis(
      new File(['pdf'], 'file.pdf', { type: 'application/pdf' }), pdfKey
    ).subscribe();
    const pdf = http.expectOne(`${environment.apiBaseUrl}/analyses/pdf`);
    expect(pdf.request.withCredentials).toBeTrue();
    expect(pdf.request.headers.get('Idempotency-Key')).toBe(pdfKey);

    const audioKey = '5515a0b7-df8c-49c7-954f-63f755788a56';
    service.createAudioAnalysis(
      new File(['audio'], 'file.webm', { type: 'audio/webm' }), audioKey
    ).subscribe();
    const audio = http.expectOne(`${environment.apiBaseUrl}/analyses/audio`);
    expect(audio.request.withCredentials).toBeTrue();
    expect(audio.request.headers.get('Idempotency-Key')).toBe(audioKey);

    http.expectNone(`${environment.apiBaseUrl}/standard-sessions`);
    http.expectNone(`${environment.apiBaseUrl}/standard-session/consent`);
  });

  it('uses Bearer for ADMIN without STANDARD CSRF', () => {
    const key = '05b85e44-cfb0-4c9f-bcc4-84b01e147c6a';
    service.createTextAnalysis('Descripción suficientemente extensa', key, 'admin-token').subscribe();
    const request = http.expectOne(`${environment.apiBaseUrl}/analyses/text`);
    expect(request.request.headers.get('Authorization')).toBe('Bearer admin-token');
    expect(request.request.headers.get('Idempotency-Key')).toBe(key);
    expect(request.request.withCredentials).toBeFalse();
    http.expectNone(`${environment.apiBaseUrl}/standard-session/csrf`);
  });
});
