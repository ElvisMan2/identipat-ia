import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { environment } from '../../environments/environment';
import { LegalDocumentService } from './legal-document.service';

describe('LegalDocumentService', () => {
  let service: LegalDocumentService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(LegalDocumentService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('gets the public and admin documents and updates the admin document with Bearer', () => {
    service.getCurrent().subscribe();
    expect(http.expectOne(`${environment.apiBaseUrl}/legal-document`).request.method).toBe('GET');

    service.getAdminCurrent('admin-token').subscribe();
    const adminGet = http.expectOne(`${environment.apiBaseUrl}/admin/legal-document`);
    expect(adminGet.request.headers.get('Authorization')).toBe('Bearer admin-token');

    service.updateAdmin('admin-token', { title: 'Legal', contentHtml: '<p>Contenido</p>' }).subscribe();
    const update = http.expectOne(`${environment.apiBaseUrl}/admin/legal-document`);
    expect(update.request.method).toBe('PUT');
    expect(update.request.headers.get('Authorization')).toBe('Bearer admin-token');
    expect(update.request.body).toEqual({ title: 'Legal', contentHtml: '<p>Contenido</p>' });
  });
});
