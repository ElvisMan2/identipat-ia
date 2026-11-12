import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CKEditorComponent } from '@ckeditor/ckeditor5-angular';
import { firstValueFrom, of } from 'rxjs';
import { App } from './app';
import { AdminAnalysisService } from './services/admin-analysis.service';
import { AdminReportDisclaimerService } from './services/admin-report-disclaimer.service';
import { AdminProtectionGuideService } from './services/admin-protection-guide.service';
import { StandardAnalysisService } from './services/standard-analysis.service';
import { UserService } from './services/user.service';
import { environment } from '../environments/environment';

function createAppWithBackendAnalysis() {
  const fixture = TestBed.createComponent(App);
  Object.defineProperty(fixture.componentInstance, 'useExampleAnalysis', { value: false });
  return fixture;
}

async function waitForEditorsToBeReady(fixture: ComponentFixture<App>): Promise<void> {
  const readyEvents = fixture.debugElement
    .queryAll(By.directive(CKEditorComponent))
    .map(debugElement => firstValueFrom((debugElement.componentInstance as CKEditorComponent).ready));
  await Promise.all(readyEvents);
}

function withoutPhone<T extends { phone?: unknown }>(user: T): Omit<T, 'phone'> {
  const { phone, ...payload } = user;
  void phone;
  return payload;
}

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should open the admin panel on Predictions by default', () => {
    const app = TestBed.createComponent(App).componentInstance;
    spyOn(app, 'loadUsers');
    const loadAnalyses = spyOn(app, 'loadAnalyses');
    spyOn<any>(app, 'loadAdminProfile');
    spyOn<any>(app, 'scrollToTop');
    spyOn(window, 'setTimeout').and.callFake(callback => {
      if (typeof callback === 'function') {
        callback();
      }
      return 0;
    });

    app.openAdminPanel();

    expect(app.view()).toBe('admin');
    expect(app.adminSection()).toBe('predictions');
    expect(loadAnalyses).toHaveBeenCalled();
  });

  it('should limit the analysis summary to 150 characters with an ellipsis', () => {
    const app = TestBed.createComponent(App).componentInstance;

    expect(app.analysisSummary('a'.repeat(150))).toBe('a'.repeat(150));
    expect(app.analysisSummary('a'.repeat(151))).toBe(`${'a'.repeat(147)}...`);
    expect(app.analysisSummary(null)).toBe('Aún no disponible.');
  });

  it('renders the public access component and forwards registration', () => {
    const fixture = TestBed.createComponent(App);
    const openRegistration = spyOn(fixture.componentInstance, 'openRegistration');
    fixture.detectChanges();

    const registerButton = fixture.nativeElement.querySelector('.access-register-button') as HTMLButtonElement;
    registerButton.click();

    expect(fixture.nativeElement.querySelector('app-public-access-page')).not.toBeNull();
    expect(openRegistration).toHaveBeenCalledTimes(1);
  });

  it('retries the registration legal document through its registration handler', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.view.set('registration');
    app.legalError.set('Documento no disponible');
    const openRegistration = spyOn(app, 'openRegistration');
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('.registration-legal-status button') as HTMLButtonElement).click();

    expect(openRegistration).toHaveBeenCalledTimes(1);
  });

  it('renders the standard analysis component and forwards submission', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const submitAnalysis = spyOn(app, 'submitAnalysis');
    app.view.set('standard-analysis');
    fixture.detectChanges();

    const form = fixture.nativeElement.querySelector('app-standard-analysis-page form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    expect(submitAnalysis).toHaveBeenCalledTimes(1);
  });

  it('connects the admin user status filter to App state and reload', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const loadUsers = spyOn(app, 'loadUsers');
    app.view.set('admin');
    app.adminSection.set('users');
    fixture.detectChanges();

    const statusFilter = fixture.nativeElement.querySelector('app-admin-users-section select') as HTMLSelectElement;
    statusFilter.value = 'I';
    statusFilter.dispatchEvent(new Event('change', { bubbles: true }));

    expect(app.userStatus()).toBe('I');
    expect(loadUsers).toHaveBeenCalledWith(0);
  });

  it('maps settings actions and legal content into the settings view model', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const input = document.createElement('input');
    input.value = 'Términos actualizados';
    const event = new Event('input');
    Object.defineProperty(event, 'target', { value: input });

    app.handleAdminSettingsAction({ type: 'legalTitle', event });
    app.legalAdminHtml.set('<p>Contenido legal</p>');

    expect(app.adminSettingsViewModel().legal.title).toBe('Términos actualizados');
    expect(app.adminSettingsViewModel().legal.content).toBe('<p>Contenido legal</p>');
  });

  it('should fetch an admin diagnostic report by analysis id', () => {
    const service = TestBed.inject(AdminAnalysisService);
    const httpTesting = TestBed.inject(HttpTestingController);
    service.setAccessToken('test-token');

    service.getReport('analysis-123').subscribe();

    const reportRequest = httpTesting.expectOne(
      `${environment.apiBaseUrl}/analyses/analysis-123/report`
    );
    expect(reportRequest.request.method).toBe('GET');
    expect(reportRequest.request.headers.get('Authorization')).toBe('Bearer test-token');
    reportRequest.flush({});
  });

  it('should fetch the paginated admin analysis history with filters and bearer token', () => {
    const service = TestBed.inject(AdminAnalysisService);
    const httpTesting = TestBed.inject(HttpTestingController);

    service.findAll(1, 5, 'patente', '2026-10-01', '2026-10-31', 'test-token').subscribe();

    const request = httpTesting.expectOne(req => req.url === `${environment.apiBaseUrl}/admin/analyses`);
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('5');
    expect(request.request.params.get('search')).toBe('patente');
    expect(request.request.params.get('from')).toBe('2026-10-01');
    expect(request.request.params.get('to')).toBe('2026-10-31');
    request.flush({ content: [], page: 1, size: 5, totalElements: 0, totalPages: 0, first: false, last: true });
  });

  it('should download the complete admin analysis CSV with date filters and bearer token', () => {
    const service = TestBed.inject(AdminAnalysisService);
    const httpTesting = TestBed.inject(HttpTestingController);

    service.exportAll('2026-10-01', '2026-10-31', 'test-token').subscribe();

    const request = httpTesting.expectOne(
      req => req.url === `${environment.apiBaseUrl}/admin/analyses/export.csv`
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
    expect(request.request.params.get('from')).toBe('2026-10-01');
    expect(request.request.params.get('to')).toBe('2026-10-31');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['csv'], { type: 'text/csv' }), {
      headers: { 'Content-Disposition': 'attachment; filename="identipat-analisis.csv"' }
    });
  });

  it('should fetch all report disclaimers with the admin bearer token', () => {
    const service = TestBed.inject(AdminReportDisclaimerService);
    const httpTesting = TestBed.inject(HttpTestingController);

    service.getAll('test-token').subscribe();

    const disclaimerRequest = httpTesting.expectOne(
      `${environment.apiBaseUrl}/admin/report-disclaimers`
    );
    expect(disclaimerRequest.request.method).toBe('GET');
    expect(disclaimerRequest.request.headers.get('Authorization')).toBe('Bearer test-token');
    disclaimerRequest.flush([{
      id: 'disclaimer-id',
      version: 1,
      title: 'Aviso legal',
      content: 'Texto del disclaimer',
      status: 'ACTIVE',
      activatedAt: '2026-10-29T12:00:00Z'
    }]);
  });

  it('should show only the latest disclaimer when entering settings', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.view.set('admin');
    app.setAdminSection('settings');
    fixture.detectChanges();
    await waitForEditorsToBeReady(fixture);

    const disclaimersRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/admin/report-disclaimers`);
    expect(disclaimersRequest.request.method).toBe('GET');
    disclaimersRequest.flush([
      {
        id: 'old-id',
        version: 3,
        title: 'Aviso anterior',
        content: 'Contenido anterior',
        status: 'RETIRED',
        activatedAt: null
      },
      {
        id: 'latest-id',
        version: 8,
        title: 'Aviso más reciente',
        content: 'Contenido más reciente',
        status: 'DRAFT',
        activatedAt: null
      }
    ]);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(app.latestReportDisclaimer()?.id).toBe('latest-id');
    expect(app.savedDisclaimerDraft()?.id).toBe('latest-id');
    expect((fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#disclaimer-title')?.value)
      .toBe('Aviso más reciente');
    expect(text).not.toContain('Estado:');
    expect(text).not.toContain('Última versión guardada:');
    expect(text).not.toContain('Activado:');
    expect(text).not.toContain('Aviso anterior');
  });

  it('should create a report disclaimer draft with the admin bearer token', () => {
    const service = TestBed.inject(AdminReportDisclaimerService);
    const httpTesting = TestBed.inject(HttpTestingController);

    service.createDraft('test-token', 'Aviso legal', 'Texto actualizado').subscribe();

    const draftRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/admin/report-disclaimers`);
    expect(draftRequest.request.method).toBe('POST');
    expect(draftRequest.request.headers.get('Authorization')).toBe('Bearer test-token');
    expect(draftRequest.request.body).toEqual({ title: 'Aviso legal', content: 'Texto actualizado' });
    draftRequest.flush({
      id: 'draft-id',
      version: 2,
      title: 'Aviso legal',
      content: 'Texto actualizado',
      status: 'DRAFT',
      activatedAt: null
    }, { status: 201, statusText: 'Created' });
  });

  it('should activate a report disclaimer draft with the admin bearer token', () => {
    const service = TestBed.inject(AdminReportDisclaimerService);
    const httpTesting = TestBed.inject(HttpTestingController);

    service.activate('test-token', 'draft-id').subscribe();

    const activationRequest = httpTesting.expectOne(
      `${environment.apiBaseUrl}/admin/report-disclaimers/draft-id/activate`
    );
    expect(activationRequest.request.method).toBe('POST');
    expect(activationRequest.request.headers.get('Authorization')).toBe('Bearer test-token');
    expect(activationRequest.request.body).toBeNull();
    activationRequest.flush({
      id: 'draft-id',
      version: 2,
      title: 'Aviso legal',
      content: 'Texto actualizado',
      status: 'ACTIVE',
      activatedAt: '2026-10-29T13:00:00Z'
    });
  });

  it('should save disclaimer title and content together as a draft', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const disclaimerService = TestBed.inject(AdminReportDisclaimerService);
    const activeDisclaimer = {
      id: 'active-id',
      version: 1,
      title: 'Aviso legal',
      content: 'Texto original',
      status: 'ACTIVE',
      activatedAt: '2026-10-29T12:00:00Z'
    };
    spyOn(disclaimerService, 'createDraft').and.returnValue(of({
      ...activeDisclaimer,
      id: 'draft-id',
      version: 2,
      title: 'Aviso actualizado',
      content: 'Texto actualizado',
      status: 'DRAFT',
      activatedAt: null
    }));
    spyOn(disclaimerService, 'activate').and.returnValue(of({
      ...activeDisclaimer,
      id: 'draft-id',
      version: 2,
      content: 'Texto actualizado',
      status: 'ACTIVE',
      activatedAt: '2026-10-29T13:00:00Z'
    }));
    const reloadSchedule = spyOn<any>(app, 'scheduleAdminSettingsReload');
    app.view.set('admin');
    app.adminSection.set('settings');
    app.latestReportDisclaimer.set(activeDisclaimer);
    app.disclaimerTitle.set(activeDisclaimer.title);
    app.savedDisclaimerTitle.set(activeDisclaimer.title);
    app.disclaimerContent.set(activeDisclaimer.content);
    app.savedDisclaimerContent.set(activeDisclaimer.content);
    fixture.detectChanges();
    await waitForEditorsToBeReady(fixture);

    const root = fixture.nativeElement as HTMLElement;
    const titleInput = root.querySelector<HTMLInputElement>('input#disclaimer-title')!;
    const textarea = root.querySelector<HTMLTextAreaElement>('textarea#disclaimer-content')!;
    const saveButton = textarea.closest('article')!.querySelector<HTMLButtonElement>('button')!;
    expect(saveButton.disabled).toBeTrue();

    titleInput.value = 'Aviso actualizado';
    titleInput.dispatchEvent(new Event('input'));
    textarea.value = 'Texto actualizado';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(saveButton.disabled).toBeFalse();

    saveButton.click();
    fixture.detectChanges();

    expect(disclaimerService.createDraft).toHaveBeenCalledWith('', 'Aviso actualizado', 'Texto actualizado');
    expect(saveButton.disabled).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Borrador guardado correctamente.');
    expect(fixture.nativeElement.textContent).not.toContain('Borrador v2');

    const publishButton = Array.from(root.querySelectorAll<HTMLButtonElement>('button'))
      .find(button => button.textContent?.includes('Publicar'))!;
    expect(publishButton.disabled).toBeFalse();
    publishButton.click();
    fixture.detectChanges();

    expect(root.querySelector('[role="dialog"]')).not.toBeNull();
    expect(disclaimerService.activate).not.toHaveBeenCalled();
    const confirmButton = Array.from(root.querySelectorAll<HTMLButtonElement>('button'))
      .find(button => button.textContent?.includes('Confirmar publicación'))!;
    confirmButton.click();
    fixture.detectChanges();

    expect(disclaimerService.activate).toHaveBeenCalledWith('', 'draft-id');
    expect(root.querySelector('[role="dialog"]')).toBeNull();
    expect(app.latestReportDisclaimer()?.status).toBe('ACTIVE');
    expect(app.savedDisclaimerDraft()).toBeNull();
    expect(root.textContent).toContain('Disclaimer publicado correctamente.');
    expect(reloadSchedule).toHaveBeenCalled();
  });

  it('should request a report PDF with ADMIN bearer or STANDARD session credentials', () => {
    const service = TestBed.inject(StandardAnalysisService);
    const httpTesting = TestBed.inject(HttpTestingController);

    service.getReportPdf('admin-analysis', 'test-token').subscribe();
    const adminRequest = httpTesting.expectOne(
      `${environment.apiBaseUrl}/analyses/admin-analysis/report.pdf`
    );
    expect(adminRequest.request.method).toBe('GET');
    expect(adminRequest.request.responseType).toBe('blob');
    expect(adminRequest.request.headers.get('Authorization')).toBe('Bearer test-token');
    adminRequest.flush(new Blob(['pdf'], { type: 'application/pdf' }));

    service.getReportPdf('standard-analysis').subscribe();
    const standardRequest = httpTesting.expectOne(
      `${environment.apiBaseUrl}/analyses/standard-analysis/report.pdf`
    );
    expect(standardRequest.request.withCredentials).toBeTrue();
    standardRequest.flush(new Blob(['pdf'], { type: 'application/pdf' }));
  });

  it('should request the diagnostic report and guidance with the matching credentials', () => {
    const service = TestBed.inject(StandardAnalysisService);
    const httpTesting = TestBed.inject(HttpTestingController);

    service.getReport('admin-analysis', 'test-token').subscribe();
    const adminReportRequest = httpTesting.expectOne(
      `${environment.apiBaseUrl}/analyses/admin-analysis/report`
    );
    expect(adminReportRequest.request.method).toBe('GET');
    expect(adminReportRequest.request.headers.get('Authorization')).toBe('Bearer test-token');
    adminReportRequest.flush({});

    service.getGuidance('standard-analysis').subscribe();
    const standardGuidanceRequest = httpTesting.expectOne(
      `${environment.apiBaseUrl}/analyses/standard-analysis/guidance`
    );
    expect(standardGuidanceRequest.request.method).toBe('GET');
    expect(standardGuidanceRequest.request.withCredentials).toBeTrue();
    standardGuidanceRequest.flush({});
  });

  it('should register opening next steps with the STANDARD session and CSRF', () => {
    const service = TestBed.inject(StandardAnalysisService);
    const httpTesting = TestBed.inject(HttpTestingController);

    service.recordGuidanceView('analysis/123', 'INVENTION_PATENT', 4).subscribe();

    const csrfRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`);
    expect(csrfRequest.request.method).toBe('GET');
    expect(csrfRequest.request.withCredentials).toBeTrue();
    csrfRequest.flush({ token: 'csrf-token', headerName: 'X-XSRF-TOKEN' });

    const viewRequest = httpTesting.expectOne(
      `${environment.apiBaseUrl}/analyses/analysis%2F123/guidance/INVENTION_PATENT/view`
    );
    expect(viewRequest.request.method).toBe('POST');
    expect(viewRequest.request.body).toEqual({ guideVersion: 4 });
    expect(viewRequest.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
    expect(viewRequest.request.withCredentials).toBeTrue();
    viewRequest.flush({
      analysisId: 'analysis/123',
      protectionType: 'INVENTION_PATENT',
      guideVersion: 4,
      firstViewedAt: '2026-11-08T00:00:00Z'
    });
  });

  it('should remove non-numeric characters from a DNI during registration', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const input = document.createElement('input');
    input.value = '12AB3456789';

    app.sanitizeDni({ target: input } as unknown as Event, app.userForm.controls.doi);

    expect(input.value).toBe('12345678');
    expect(app.userForm.controls.doi.value).toBe('12345678');
  });

  it('should replace editor line breaks with non-breaking spaces before storing HTML', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    app.updatePatentOrientationHtml({
      editor: { getData: () => '<p>Primero<br>Segundo<br></p>' }
    });

    expect(app.patentOrientationHtml()).toBe('<p>Primero&nbsp;Segundo&nbsp;</p>');
    expect(app.patentOrientationHtml()).not.toContain('<br');
  });

  it('should sanitize the orientation HTML before publishing a draft', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const service = TestBed.inject(AdminProtectionGuideService);
    const draft = {
      guideId: 'guide-id',
      protectionType: 'INVENTION_PATENT',
      version: 1,
      shortDescription: 'Descripción suficientemente extensa para la orientación.',
      nextStepsContentHtml: '<p><a target="_self" href="javascript:alert(1)">enlace</a><br></p>',
      status: 'DRAFT'
    };
    spyOn(service, 'activate').and.returnValue(of({ ...draft, status: 'ACTIVE' }));
    spyOn<any>(app, 'scheduleAdminSettingsReload');

    app.savedPatentOrientationDraft.set(draft);
    app.patentOrientationHtml.set(draft.nextStepsContentHtml);
    app.savedPatentOrientationHtml.set(draft.nextStepsContentHtml);
    app.patentOrientationShortDescription.set(draft.shortDescription);
    app.savedPatentOrientationShortDescription.set(draft.shortDescription);
    app.showPublishPatentOrientationModal.set(true);

    app.activatePatentOrientationDraft();

    expect(service.activate).toHaveBeenCalledWith('', 'guide-id');
    expect(app.patentOrientationHtml()).not.toContain('<br');
    expect(app.patentOrientationHtml()).toContain('href="https://example.com/"');
    expect(app.patentOrientationHtml()).not.toContain('target="_blank"');
    expect(app.patentOrientationHtml()).not.toContain('rel="noopener noreferrer"');
  });

  it('should preserve the manual new-tab link setting', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    app.updatePatentOrientationHtml({
      editor: { getData: () => '<p><a href="https://example.com/" target="_blank" rel="noopener noreferrer">enlace</a></p>' }
    });

    expect(app.patentOrientationHtml()).toContain('target="_blank"');
    expect(app.patentOrientationHtml()).toContain('rel="noopener noreferrer"');

    app.updatePatentOrientationHtml({
      editor: { getData: () => '<p><a href="https://example.com/">enlace</a></p>' }
    });

    expect(app.patentOrientationHtml()).not.toContain('target="_blank"');
    expect(app.patentOrientationHtml()).not.toContain('rel="noopener noreferrer"');
  });

  it('should create a utility model orientation draft with the utility model type', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    const shortDescription = 'Descripción suficientemente extensa para el modelo de utilidad.';

    app.utilityModelOrientationShortDescription.set(shortDescription);
    app.utilityModelOrientationHtml.set('<p>Pasos del modelo de utilidad.</p>');
    app.saveUtilityModelOrientationDraft();

    const draftRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/admin/protection-guides`);
    expect(draftRequest.request.method).toBe('POST');
    expect(draftRequest.request.body).toEqual({
      protectionType: 'UTILITY_MODEL',
      shortDescription,
      nextStepsContentHtml: '<p>Pasos del modelo de utilidad.</p>'
    });
    draftRequest.flush({
      guideId: 'utility-model-guide',
      protectionType: 'UTILITY_MODEL',
      version: 1,
      shortDescription,
      nextStepsContentHtml: '<p>Pasos del modelo de utilidad.</p>',
      status: 'DRAFT'
    }, { status: 201, statusText: 'Created' });
  });

  it('should render the home as the first screen', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h2')?.textContent)
      .toContain('¿Qué creaciones puedes analizar?');
    expect(compiled.textContent).not.toContain('Usuarios registrados');
  });

  it('should close the STANDARD session before exiting without fetching the session', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.view.set('standard-result');

    app.exitResult();

    const csrfRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`);
    expect(csrfRequest.request.method).toBe('GET');
    expect(csrfRequest.request.withCredentials).toBeTrue();
    csrfRequest.flush({ token: 'csrf-token', headerName: 'X-XSRF-TOKEN' });

    const closeRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session`);
    expect(closeRequest.request.method).toBe('DELETE');
    expect(closeRequest.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
    expect(closeRequest.request.withCredentials).toBeTrue();
    closeRequest.flush(null, { status: 204, statusText: 'No Content' });

    httpTesting.expectNone(request =>
      request.url === `${environment.apiBaseUrl}/standard-session` && request.method === 'GET'
    );
    expect(app.view()).toBe('home');
    expect(app.navigationPending()).toBeNull();
    expect(app.exitError()).toBe('');
  });

  it('should allow exiting when the STANDARD session has already expired', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.view.set('standard-result');

    app.exitResult();
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`)
      .flush({ token: 'csrf-token', headerName: 'X-XSRF-TOKEN' });
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session`)
      .flush({ code: 'STANDARD_SESSION_REQUIRED' }, { status: 401, statusText: 'Unauthorized' });

    expect(app.view()).toBe('home');
    expect(app.navigationPending()).toBeNull();
    expect(app.exitError()).toBe('');
  });

  it('should load and navigate paginated admin users', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    const pageResponse = {
      content: [{
        userId: 1,
        firstName: 'Ana',
        paternalLastName: 'Pérez',
        maternalLastName: 'Rojas',
        doi: '12345678',
        doiType: 'DNI',
        birthDate: '1990-01-01',
        gender: 'F',
        email: 'ana@example.com',
        phone: '123456789',
        mobilePhone: '987654321',
        userType: 'STANDARD',
        profession: 'Abogada',
        status: 'A'
      }],
      page: 0,
      size: 10,
      totalElements: 42,
      totalPages: 5,
      first: true,
      last: false
    };

    app.loadUsers();
    httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users`
      && request.params.get('page') === '0'
      && request.params.get('size') === '10'
      && request.params.get('sort') === 'userId,asc'
    ).flush(pageResponse);

    expect(app.users()).toEqual(pageResponse.content);
    expect(app.userTotalElements()).toBe(42);
    expect(app.userTotalPages()).toBe(5);

    app.changeUserPage(1);
    httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users`
      && request.params.get('page') === '1'
      && request.params.get('size') === '10'
    ).flush({ ...pageResponse, page: 1, first: false });

    expect(app.userPage()).toBe(1);
    expect(app.users()).toHaveSize(1);

    app.updateUserSearch({ target: { value: 'ana rojas' } } as unknown as Event);
    app.searchUsers();
    httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users`
      && request.params.get('page') === '0'
      && request.params.get('size') === '10'
      && request.params.get('search') === 'ana rojas'
    ).flush({ ...pageResponse, totalElements: 1, totalPages: 1 });

    expect(app.userPage()).toBe(0);
    expect(app.userTotalElements()).toBe(1);

    app.changeUserStatus({ target: { value: 'I' } } as unknown as Event);
    httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users`
      && request.params.get('page') === '0'
      && request.params.get('size') === '10'
      && request.params.get('search') === 'ana rojas'
      && request.params.get('status') === 'I'
    ).flush({ ...pageResponse, content: [], totalElements: 0, totalPages: 0 });

    expect(app.userStatus()).toBe('I');
    expect(app.userTotalElements()).toBe(0);

    app.changeUserType({ target: { value: 'ADMIN' } } as unknown as Event);
    httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users`
      && request.params.get('page') === '0'
      && request.params.get('size') === '10'
      && request.params.get('search') === 'ana rojas'
      && request.params.get('status') === 'I'
      && request.params.get('userType') === 'ADMIN'
    ).flush({ ...pageResponse, content: [], totalElements: 0, totalPages: 0 });

    expect(app.userType()).toBe('ADMIN');
    expect(app.userTotalElements()).toBe(0);
  });

  it('should request a password automatically when the document belongs to an admin', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    app.accessForm.setValue({ doiType: 'DNI', doi: '12345678' });
    app.identifyDocument();
    httpTesting.expectOne(`${environment.apiBaseUrl}/users/identify`).flush({
      registered: true,
      passwordRequired: true
    });
    await new Promise(resolve => setTimeout(resolve, 550));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Acceso administrativo');
    expect(fixture.nativeElement.querySelector('input[type="password"]')).not.toBeNull();
  });

  it('should show the verification modal for a registered standard user', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    app.accessForm.setValue({ doiType: 'DNI', doi: '12345678' });
    app.identifyDocument();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.access-progress-modal')).toBeNull();
    httpTesting.expectOne(`${environment.apiBaseUrl}/users/identify`).flush({
      registered: true,
      passwordRequired: false
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.access-progress-modal')).not.toBeNull();
  });

  it('should show the verification modal after an admin submits the password', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    app.view.set('admin-login');
    app.accessForm.setValue({ doiType: 'DNI', doi: '12345678' });
    app.loginForm.setValue({ password: 'secret' });
    app.loginAdmin();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.access-progress-modal')).not.toBeNull();
    httpTesting.expectOne(`${environment.apiBaseUrl}/users/login`).flush({}, {
      status: 401,
      statusText: 'Unauthorized'
    });
  });

  it('should show a message for an unknown document without opening registration', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    app.accessForm.setValue({ doiType: 'DNI', doi: '12345678' });
    app.identifyDocument();
    httpTesting.expectOne(`${environment.apiBaseUrl}/users/identify`).flush({
      registered: false,
      passwordRequired: false
    });
    expect(app.accessMessage()).toBe('');
    await new Promise(resolve => setTimeout(resolve, 500));
    fixture.detectChanges();

    expect(app.view()).toBe('home');
    expect(app.accessMessage()).toBe('Usuario no registrado');
    expect(fixture.nativeElement.textContent).toContain('Usuario no registrado');
  });

  it('should open registration directly from the access screen', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    fixture.detectChanges();

    const registerButton = fixture.nativeElement.querySelector('.access-register-button') as HTMLButtonElement;
    registerButton.click();
    fixture.detectChanges();

    expect(app.view()).toBe('registration');
    expect(fixture.nativeElement.textContent).toContain('Completa tu registro');
  });

  it('should show the analysis form and admin panel access after a successful admin login', () => {
    const userService = TestBed.inject(UserService);
    userService.clearAccessToken();
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    spyOn<any>(app, 'finishAccess').and.callFake((_startedAt: number, complete: () => void) => complete());

    app.accessForm.setValue({ doiType: 'DNI', doi: '12345678' });
    app.identifyDocument();
    httpTesting.expectOne(`${environment.apiBaseUrl}/users/identify`).flush({
      registered: true,
      passwordRequired: true
    });
    expect(app.view()).toBe('admin-login');

    app.loginForm.setValue({ password: 'secret' });
    app.loginAdmin();

    httpTesting.expectOne(`${environment.apiBaseUrl}/users/login`).flush({
      tokenType: 'Bearer',
      accessToken: 'test-token'
    });
    fixture.detectChanges();

    expect(app.view()).toBe('standard-analysis');
    expect(fixture.nativeElement.textContent).toContain('Cuéntanos qué quieres proteger');
    expect(fixture.nativeElement.textContent).toContain('Panel de administración');
    userService.clearAccessToken();
  });

  it('should load the predictions section and show its empty state', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.view.set('admin');
    app.setAdminSection('predictions');
    fixture.detectChanges();

    httpTesting.expectOne(req => req.url === `${environment.apiBaseUrl}/admin/analyses`).flush({
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Historial de predicciones');
    expect(fixture.nativeElement.textContent).toContain('No hay predicciones registradas.');
  });

  it('should create only a standard user through the registration contract', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.view.set('registration');
    app.legalDocument.set({
      documentId: 'legal-1',
      title: 'Términos vigentes',
      contentHtml: '<p>Contenido</p>',
      contentHash: 'hash-current',
      updatedAt: '2026-11-03T05:00:00Z'
    });
    app.userForm.controls.termsAccepted.enable({ emitEvent: false });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.registration-form input[name="phone"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('.registration-form input[name="mobilePhone"]')).not.toBeNull();

    app.userForm.setValue({
      firstName: 'Nombre de prueba',
      paternalLastName: 'Apellido paterno',
      maternalLastName: 'Apellido materno',
      doi: '12345678',
      doiType: 'DNI',
      birthDate: '01/01/1990',
      gender: 'FEMALE',
      email: 'standard.placeholder@example.test',
      mobilePhone: '912345678',
      profession: 'Profesión de prueba',
      password: '',
      termsAccepted: true
    });

    fixture.detectChanges();
    const createButton = fixture.nativeElement.querySelector(
      '.registration-actions button[type="submit"]'
    ) as HTMLButtonElement;
    expect(createButton.disabled).toBeFalse();
    createButton.click();
    app.registerStandard();

    const csrfRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`);
    expect(csrfRequest.request.withCredentials).toBeTrue();
    csrfRequest.flush({ token: 'csrf-token', headerName: 'X-XSRF-TOKEN' });
    const registrationRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users`);
    expect(registrationRequest.request.method).toBe('POST');
    expect(registrationRequest.request.body).toEqual({
      firstName: 'Nombre de prueba',
      paternalLastName: 'Apellido paterno',
      maternalLastName: 'Apellido materno',
      doi: '12345678',
      doiType: 'DNI',
      birthDate: '01/01/1990',
      gender: 'FEMALE',
      email: 'standard.placeholder@example.test',
      mobilePhone: '912345678',
      profession: 'Profesión de prueba',
      legalAcceptance: { contentHash: 'hash-current', accepted: true }
    });
    expect(registrationRequest.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
    expect(registrationRequest.request.withCredentials).toBeTrue();
    registrationRequest.flush({ registered: true });
    const sessionRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/standard-sessions`);
    expect(sessionRequest.request.body).toEqual({ doi: '12345678', doiType: 'DNI' });
    sessionRequest.flush({ status: 'ACTIVE', expiresAt: '', absoluteExpiresAt: '', consentRequired: false });
  });

  it('should create one STANDARD session and enter analysis without loading legal when consent is current', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.accessForm.setValue({ doiType: 'DNI', doi: '12345678' });

    app.identifyDocument();
    httpTesting.expectOne(`${environment.apiBaseUrl}/users/identify`).flush({
      registered: true, passwordRequired: false
    });
    await new Promise(resolve => setTimeout(resolve, 510));
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({
      token: 'csrf', headerName: 'X-XSRF-TOKEN'
    });
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-sessions`).flush({
      status: 'ACTIVE', expiresAt: '', absoluteExpiresAt: '', consentRequired: false
    });

    expect(app.standardAccessState()).toBe('READY');
    expect(app.view()).toBe('standard-analysis');
    httpTesting.expectNone(`${environment.apiBaseUrl}/legal-document`);
    httpTesting.expectNone(`${environment.apiBaseUrl}/standard-session/consent`);
  });

  it('should show the consent gate and enable analysis only after explicit acceptance', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.accessForm.setValue({ doiType: 'DNI', doi: '12345678' });

    app.identifyDocument();
    httpTesting.expectOne(`${environment.apiBaseUrl}/users/identify`).flush({
      registered: true, passwordRequired: false
    });
    await new Promise(resolve => setTimeout(resolve, 510));
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({
      token: 'csrf', headerName: 'X-XSRF-TOKEN'
    });
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-sessions`).flush({
      status: 'ACTIVE', expiresAt: '', absoluteExpiresAt: '', consentRequired: true
    });
    httpTesting.expectOne(`${environment.apiBaseUrl}/legal-document`).flush({
      documentId: 'legal-a', title: 'Legal A', contentHtml: '<p>A</p>', contentHash: 'hash-a', updatedAt: '2026-11-03T05:00:00Z'
    });

    expect(app.standardAccessState()).toBe('CONSENT_REQUIRED');
    expect(app.consentChecked()).toBeFalse();
    app.consentChecked.set(true);
    app.acceptLegalDocument();
    const consent = httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/consent`);
    expect(consent.request.body).toEqual({ contentHash: 'hash-a', decision: 'ACCEPTED' });
    consent.flush({ contentHash: 'hash-a', decision: 'ACCEPTED', acceptedAt: '', sessionStatus: 'ACTIVE', consentRequired: false });

    expect(app.standardAccessState()).toBe('READY');
  });

  it('should keep the session blocked after an explicit rejection', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.standardAccessState.set('CONSENT_REQUIRED');
    app.legalDocument.set({
      documentId: 'legal-a', title: 'Legal A', contentHtml: '<p>A</p>', contentHash: 'hash-a', updatedAt: '2026-11-03T05:00:00Z'
    });

    app.rejectLegalDocument();
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({ token: 'csrf', headerName: 'X-XSRF-TOKEN' });
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/consent`).flush({
      contentHash: 'hash-a', decision: 'REJECTED', acceptedAt: null, sessionStatus: 'ACTIVE', consentRequired: true
    });

    expect(app.standardAccessState()).toBe('BLOCKED');
    expect(app.consentMessage()).toContain('es necesario aceptar');
  });

  it('should replace a stale legal document and uncheck consent', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.standardAccessState.set('CONSENT_REQUIRED');
    app.legalDocument.set({
      documentId: 'legal-a', title: 'Legal A', contentHtml: '<p>A</p>', contentHash: 'hash-a', updatedAt: '2026-11-03T05:00:00Z'
    });
    app.consentChecked.set(true);

    app.acceptLegalDocument();
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({ token: 'csrf', headerName: 'X-XSRF-TOKEN' });
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/consent`).flush(
      { code: 'LEGAL_DOCUMENT_CHANGED' }, { status: 409, statusText: 'Conflict' }
    );
    httpTesting.expectOne(`${environment.apiBaseUrl}/legal-document`).flush({
      documentId: 'legal-b', title: 'Legal B', contentHtml: '<p>B</p>', contentHash: 'hash-b', updatedAt: '2026-11-03T06:00:00Z'
    });

    expect(app.legalDocument()?.contentHash).toBe('hash-b');
    expect(app.consentChecked()).toBeFalse();
    expect(app.standardAccessState()).toBe('CONSENT_REQUIRED');
  });

  it('should preserve analysis input and avoid automatic retry when consent becomes required', () => {
    const fixture = createAppWithBackendAnalysis();
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.standardAccessState.set('READY');
    app.view.set('standard-analysis');
    app.analysisForm.setValue({ description: 'Descripción suficientemente extensa para analizar' });

    app.submitAnalysis();
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({ token: 'csrf', headerName: 'X-XSRF-TOKEN' });
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/text`).flush(
      { code: 'CONSENT_REQUIRED' }, { status: 409, statusText: 'Conflict' }
    );
    httpTesting.expectOne(`${environment.apiBaseUrl}/legal-document`).flush({
      documentId: 'legal-b', title: 'Legal B', contentHtml: '<p>B</p>', contentHash: 'hash-b', updatedAt: '2026-11-03T06:00:00Z'
    });

    expect(app.analysisForm.controls.description.value).toContain('Descripción suficientemente');
    expect(app.standardAccessState()).toBe('CONSENT_REQUIRED');
    httpTesting.expectNone(`${environment.apiBaseUrl}/analyses/text`);

    app.consentChecked.set(true);
    app.acceptLegalDocument();
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/consent`).flush({
      contentHash: 'hash-b', decision: 'ACCEPTED', acceptedAt: '', sessionStatus: 'ACTIVE', consentRequired: false
    });

    expect(app.standardAccessState()).toBe('READY');
    expect(app.analysisForm.controls.description.value).toContain('Descripción suficientemente');
    httpTesting.expectNone(`${environment.apiBaseUrl}/analyses/text`);
  });

  it('should reuse the idempotency key after an uncertain network error and replace it for changed text', () => {
    const fixture = createAppWithBackendAnalysis();
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.standardAccessState.set('READY');
    app.view.set('standard-analysis');
    app.analysisForm.setValue({ description: 'Descripción inicial suficientemente extensa para analizar' });

    app.submitAnalysis();
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({
      token: 'csrf', headerName: 'X-XSRF-TOKEN'
    });
    const first = httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/text`);
    const firstKey = first.request.headers.get('Idempotency-Key');
    expect(firstKey).toMatch(/^[0-9a-f-]{36}$/);
    first.error(new ProgressEvent('network error'));
    expect(app.analysisMessage()).toContain('Reintenta sin cambiar el contenido');

    app.submitAnalysis();
    const retry = httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/text`);
    expect(retry.request.headers.get('Idempotency-Key')).toBe(firstKey);
    retry.error(new ProgressEvent('network error'));

    app.analysisForm.setValue({ description: 'Descripción modificada suficientemente extensa para analizar' });
    app.submitAnalysis();
    const changed = httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/text`);
    expect(changed.request.headers.get('Idempotency-Key')).not.toBe(firstKey);
    changed.flush({ analysisId: 'analysis-changed', status: 'RECEIVED', createdAt: '' });
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/analysis-changed`).flush({
      analysisId: 'analysis-changed', status: 'FAILED', createdAt: ''
    });
  });

  it('should resume polling the accepted analysis instead of posting another request', () => {
    const fixture = createAppWithBackendAnalysis();
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.standardAccessState.set('READY');
    app.view.set('standard-analysis');
    app.analysisForm.setValue({ description: 'Descripción suficientemente extensa para reanudar seguimiento' });

    app.submitAnalysis();
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({
      token: 'csrf', headerName: 'X-XSRF-TOKEN'
    });
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/text`).flush({
      analysisId: 'analysis-resume', status: 'RECEIVED', createdAt: ''
    });
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/analysis-resume`).flush(
      { code: 'ANALYSIS_TEMPORARILY_UNAVAILABLE' },
      { status: 503, statusText: 'Service Unavailable' }
    );

    expect(app.pendingAnalysisId()).toBe('analysis-resume');
    expect(app.analysisMessage()).toContain('Reanuda el seguimiento');
    app.submitAnalysis();
    httpTesting.expectNone(`${environment.apiBaseUrl}/analyses/text`);
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/analysis-resume`).flush({
      analysisId: 'analysis-resume', status: 'FAILED', createdAt: ''
    });
  });

  it('should stop offering polling resume when text changes after a polling failure', () => {
    const fixture = createAppWithBackendAnalysis();
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.standardAccessState.set('READY');
    app.view.set('standard-analysis');
    app.analysisForm.setValue({ description: 'Descripción original suficientemente extensa para seguimiento' });

    app.submitAnalysis();
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({
      token: 'csrf', headerName: 'X-XSRF-TOKEN'
    });
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/text`).flush({
      analysisId: 'analysis-original', status: 'RECEIVED', createdAt: ''
    });
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/analysis-original`).flush(
      { code: 'ANALYSIS_TEMPORARILY_UNAVAILABLE' },
      { status: 503, statusText: 'Service Unavailable' }
    );
    fixture.detectChanges();

    const submitButton = () => (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('form.analysis-form button[type="submit"]');
    expect(app.pendingAnalysisId()).toBe('analysis-original');
    expect(app.canResumePendingAnalysis()).toBeTrue();
    expect(submitButton()?.textContent).toContain('Reanudar seguimiento');

    app.analysisForm.controls.description.setValue(
      'Descripción modificada suficientemente extensa que constituye otra intención'
    );
    fixture.detectChanges();

    expect(app.pendingAnalysisId()).toBe('analysis-original');
    expect(app.canResumePendingAnalysis()).toBeFalse();
    expect(submitButton()?.textContent).toContain('Iniciar análisis');

    app.submitAnalysis();
    httpTesting.expectNone(`${environment.apiBaseUrl}/analyses/analysis-original`);
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/text`).flush({
      analysisId: 'analysis-new-intent', status: 'RECEIVED', createdAt: ''
    });
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/analysis-new-intent`).flush({
      analysisId: 'analysis-new-intent', status: 'FAILED', createdAt: ''
    });
  });

  it('should show quota, capacity and idempotency messages with defensive Retry-After handling', () => {
    const fixture = createAppWithBackendAnalysis();
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.standardAccessState.set('READY');
    app.view.set('standard-analysis');
    app.analysisForm.setValue({ description: 'Descripción suficientemente extensa para probar límites' });

    app.submitAnalysis();
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({
      token: 'csrf', headerName: 'X-XSRF-TOKEN'
    });
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/text`).flush(
      { code: 'ANALYSIS_QUOTA_EXCEEDED' },
      { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '120' } }
    );
    expect(app.analysisMessage()).toContain('aproximadamente 2 minutos');

    app.submitAnalysis();
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/text`).flush(
      { code: 'IDEMPOTENCY_REQUEST_IN_PROGRESS' },
      { status: 409, statusText: 'Conflict', headers: { 'Retry-After': 'invalid' } }
    );
    expect(app.analysisMessage()).toContain('reintenta en unos segundos');

    app.submitAnalysis();
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/text`).flush(
      { code: 'ANALYSIS_CAPACITY_EXCEEDED' },
      { status: 503, statusText: 'Service Unavailable', headers: { 'Retry-After': '10' } }
    );
    expect(app.analysisMessage()).toContain('servicio está ocupado');
  });

  it('should reuse one STANDARD session for a second analysis without creating or accepting anything again', () => {
    const fixture = createAppWithBackendAnalysis();
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.standardAccessState.set('READY');
    app.view.set('standard-analysis');
    app.analysisForm.setValue({ description: 'Primera descripción suficientemente extensa para analizar' });

    app.submitAnalysis();
    app.submitAnalysis();
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({
      token: 'csrf', headerName: 'X-XSRF-TOKEN'
    });
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/text`).flush({
      analysisId: 'analysis-1', status: 'RECEIVED', createdAt: ''
    });
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/analysis-1`).flush({
      analysisId: 'analysis-1', status: 'FAILED', createdAt: ''
    });

    app.startNewAnalysis();
    app.analysisForm.setValue({ description: 'Segunda descripción suficientemente extensa para analizar' });
    app.submitAnalysis();
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/text`).flush({
      analysisId: 'analysis-2', status: 'RECEIVED', createdAt: ''
    });
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/analysis-2`).flush({
      analysisId: 'analysis-2', status: 'FAILED', createdAt: ''
    });

    httpTesting.expectNone(`${environment.apiBaseUrl}/standard-sessions`);
    httpTesting.expectNone(`${environment.apiBaseUrl}/standard-session/consent`);
  });

  it('should clear local STANDARD state when polling reports an expired session', () => {
    const fixture = createAppWithBackendAnalysis();
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.standardAccessState.set('READY');
    app.view.set('standard-analysis');
    app.analysisForm.setValue({ description: 'Descripción suficientemente extensa para analizar' });

    app.submitAnalysis();
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({
      token: 'csrf', headerName: 'X-XSRF-TOKEN'
    });
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/text`).flush({
      analysisId: 'analysis-expired', status: 'RECEIVED', createdAt: ''
    });
    httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/analysis-expired`).flush(
      { code: 'STANDARD_SESSION_REQUIRED' }, { status: 401, statusText: 'Unauthorized' }
    );

    expect(app.view()).toBe('home');
    expect(app.standardAccessState()).toBe('UNIDENTIFIED');
    expect(app.analysisForm.controls.description.value).toBe('');
  });

  it('should keep registration blocked when the legal document is unavailable', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);

    app.openRegistration();
    httpTesting.expectOne(`${environment.apiBaseUrl}/legal-document`).flush(
      { code: 'LEGAL_DOCUMENT_NOT_FOUND' }, { status: 404, statusText: 'Not Found' }
    );
    fixture.detectChanges();

    const submit = fixture.nativeElement.querySelector(
      '.registration-actions button[type="submit"]'
    ) as HTMLButtonElement;
    expect(app.legalDocument()).toBeNull();
    expect(app.userForm.controls.termsAccepted.disabled).toBeTrue();
    expect(submit.disabled).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('aún no están disponibles');
  });

  it('should keep analysis blocked when the legal document is unavailable for consent', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.view.set('standard-analysis');
    app.standardAccessState.set('CONSENT_REQUIRED');

    app.reviewLegalDocument();
    httpTesting.expectOne(`${environment.apiBaseUrl}/legal-document`).flush(
      { code: 'LEGAL_DOCUMENT_NOT_FOUND' }, { status: 404, statusText: 'Not Found' }
    );
    fixture.detectChanges();

    const analysis = fixture.nativeElement.querySelector('.analysis-shell') as HTMLElement;
    expect(app.standardAccessState()).toBe('BLOCKED');
    expect(app.legalDocument()).toBeNull();
    expect(analysis.hasAttribute('inert')).toBeTrue();
    expect(analysis.getAttribute('aria-hidden')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('Documento legal no disponible');
  });

  it('should render the public legal source from the backend state', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.view.set('legal');

    app.loadPublicLegalDocument();
    httpTesting.expectOne(`${environment.apiBaseUrl}/legal-document`).flush({
      documentId: 'legal-dynamic',
      title: 'Documento legal dinámico',
      contentHtml: '<h2>Contenido vigente</h2><p>Texto dinámico</p>',
      contentHash: 'hash-dynamic',
      updatedAt: '2026-11-03T06:00:00Z'
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Documento legal dinámico');
    expect(fixture.nativeElement.textContent).toContain('Texto dinámico');
    expect(fixture.nativeElement.querySelector('app-legal-page')).not.toBeNull();
  });

  it('should render the analysis result and forward its actions', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.view.set('standard-result');
    app.analysisResult.set({
      analysisId: 'analysis-result',
      inputType: 'TEXT',
      status: 'COMPLETED',
      createdAt: '2026-11-03T06:00:00Z',
      startedAt: '2026-11-03T06:00:00Z',
      completedAt: '2026-11-03T06:01:00Z',
      failedAt: null,
      result: null,
      failure: null
    });
    app.diagnosticReport.set({
      reportVersion: null,
      analysisId: 'analysis-result',
      sourceResultVersion: null,
      inputType: 'TEXT',
      analysisCompletedAt: '2026-11-03T06:01:00Z',
      title: 'Diagnóstico de prueba',
      summary: 'Resumen de prueba',
      applicabilityNote: null,
      protectionOptions: [{
        type: 'INVENTION_PATENT',
        name: 'Patente de invención',
        relationshipQuestion: null,
        applicability: 'POSSIBLE',
        applicabilityLabel: 'Posible',
        protectedSubjectMatter: 'Una creación',
        rationale: 'Cumple criterios',
        legalBasis: null,
        orientation: null
      }],
      patentScreening: null,
      geneticResourceAccess: null,
      historicalPatentability: null,
      observations: null,
      warnings: null,
      scope: null,
      disclaimer: { id: null, version: null, title: 'Aviso', content: 'Contenido del aviso' }
    });
    const guidance = {
      guidanceVersion: 'v1',
      analysisId: 'analysis-result',
      resolvedAt: '2026-11-03T06:01:00Z',
      modalities: [{
        protectionType: 'INVENTION_PATENT',
        name: 'Patente de invención',
        applicability: 'POSSIBLE',
        applicabilityLabel: 'Posible',
        guideVersion: 1,
        shortDescription: 'Protección de una invención',
        nextSteps: { contentHtml: '<p>Revisar requisitos</p>' },
        lastVerifiedAt: '2026-11-03T06:01:00Z'
      }]
    };
    app.analysisGuidance.set(guidance);
    const downloadReport = spyOn(app, 'downloadReportPdf');
    const openNextSteps = spyOn(app, 'openNextStepsModal');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-analysis-result-page')).not.toBeNull();
    expect(compiled.textContent).toContain('Diagnóstico de prueba');
    expect(compiled.textContent).toContain('Resumen de prueba');
    compiled.querySelector<HTMLButtonElement>('.analysis-result .hero-main .outline-button')?.click();
    compiled.querySelector<HTMLButtonElement>('.modality-next-steps')?.click();

    expect(downloadReport).toHaveBeenCalled();
    expect(openNextSteps).toHaveBeenCalledWith(
      'Patente de invención',
      'INVENTION_PATENT',
      guidance.modalities[0]
    );
  });

  it('should retry loading the public legal document from the legal page', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.view.set('legal');
    app.legalError.set('No se pudo cargar el documento legal.');
    fixture.detectChanges();

    const retryButton = fixture.nativeElement.querySelector('.legal-state-error button') as HTMLButtonElement;
    retryButton.click();

    const request = httpTesting.expectOne(`${environment.apiBaseUrl}/legal-document`);
    expect(request.request.method).toBe('GET');
    request.flush({
      documentId: 'legal-retry',
      title: 'Documento recuperado',
      contentHtml: '<p>Contenido recuperado</p>',
      contentHash: 'hash-retry',
      updatedAt: '2026-11-03T06:00:00Z'
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Documento recuperado');
  });

  it('should allow first ADMIN legal configuration and replace local HTML with the sanitized response', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);

    app.loadAdminLegalDocument();
    httpTesting.expectOne(`${environment.apiBaseUrl}/admin/legal-document`).flush(
      { code: 'LEGAL_DOCUMENT_NOT_FOUND' }, { status: 404, statusText: 'Not Found' }
    );
    expect(app.legalAdminDocument()).toBeNull();

    app.legalAdminTitle.set('Documento legal');
    app.legalAdminHtml.set('<p onclick="bad()">Contenido</p>');
    app.saveAdminLegalDocument();
    const update = httpTesting.expectOne(`${environment.apiBaseUrl}/admin/legal-document`);
    expect(update.request.body).toEqual({
      title: 'Documento legal', contentHtml: '<p onclick="bad()">Contenido</p>'
    });
    update.flush({
      documentId: 'legal-1',
      title: 'Documento legal',
      contentHtml: '<p>Contenido</p>',
      contentHash: 'hash-1',
      updatedAt: '2026-11-03T06:00:00Z'
    });

    expect(app.legalAdminHtml()).toBe('<p>Contenido</p>');
    expect(app.legalAdminMessage()).toContain('guardado correctamente');
  });

  it('should preserve registration data and require a fresh checkbox after a legal change', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.view.set('registration');
    app.legalDocument.set({
      documentId: 'legal-a', title: 'Legal A', contentHtml: '<p>A</p>', contentHash: 'hash-a', updatedAt: '2026-11-03T05:00:00Z'
    });
    app.userForm.setValue({
      firstName: 'Ana', paternalLastName: 'Rojas', maternalLastName: 'López', doi: '12345678', doiType: 'DNI',
      birthDate: '01/01/1990', gender: 'FEMALE', email: 'ana@example.test',
      mobilePhone: '912345678', profession: 'Abogada', password: '', termsAccepted: true
    });

    app.registerStandard();
    httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({ token: 'csrf', headerName: 'X-XSRF-TOKEN' });
    httpTesting.expectOne(`${environment.apiBaseUrl}/users`).flush(
      { code: 'LEGAL_DOCUMENT_CHANGED' }, { status: 409, statusText: 'Conflict' }
    );
    httpTesting.expectOne(`${environment.apiBaseUrl}/legal-document`).flush({
      documentId: 'legal-b', title: 'Legal B', contentHtml: '<p>B</p>', contentHash: 'hash-b', updatedAt: '2026-11-03T06:00:00Z'
    });

    expect(app.userForm.controls.firstName.value).toBe('Ana');
    expect(app.userForm.controls.termsAccepted.value).toBeFalse();
    expect(app.accessMessage()).toContain('actualizados');
  });

  it('should inactivate a user through an update without deleting it', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    const user = {
      userId: 7,
      firstName: 'Nombre',
      paternalLastName: 'Apellido',
      maternalLastName: 'Materno',
      doi: '12345678',
      doiType: 'DNI',
      birthDate: '01/01/1990',
      gender: 'FEMALE',
      email: 'user@example.test',
      phone: '016543210',
      mobilePhone: '912345678',
      userType: 'STANDARD',
      profession: 'Profesión',
      status: 'A'
    };
    spyOn(window, 'confirm').and.returnValue(true);

    app.toggleStatus(user);

    const updateRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users/admin/7`);
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body).toEqual({ ...withoutPhone(user), status: 'I' });
    httpTesting.expectNone(`${environment.apiBaseUrl}/users/7`);
    updateRequest.flush({ ...user, status: 'I' });
    const reloadRequest = httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users` && request.method === 'GET'
    );
    expect(reloadRequest.request.params.get('page')).toBe('0');
    expect(reloadRequest.request.params.get('size')).toBe('10');
    reloadRequest.flush({
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true
    });
  });

  it('should toggle the edited user status through an update', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    const user = {
      userId: 9,
      firstName: 'Nombre',
      paternalLastName: 'Apellido',
      maternalLastName: 'Materno',
      doi: '12345678',
      doiType: 'DNI',
      birthDate: '01/01/1990',
      gender: 'FEMALE',
      email: 'user@example.test',
      phone: '016543210',
      mobilePhone: '912345678',
      userType: 'STANDARD',
      profession: 'Profesión',
      status: 'A'
    };
    spyOn(window, 'confirm').and.returnValue(true);
    app.view.set('admin');
    app.adminSection.set('users');
    app.users.set([user]);
    app.startEdit(user);
    fixture.detectChanges();
    const initialDialog = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement | null;
    expect(initialDialog).not.toBeNull();
    expect(initialDialog?.textContent).toContain('Inactivar');
    expect(initialDialog?.querySelector('input[formControlName="mobilePhone"]')).not.toBeNull();
    expect(initialDialog?.querySelector('input[formControlName="phone"]')).toBeNull();

    app.toggleEditingUserStatus();

    const inactivateRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users/admin/9`);
    expect(inactivateRequest.request.method).toBe('PUT');
    expect(inactivateRequest.request.body.status).toBe('I');
    expect(inactivateRequest.request.body.phone).toBeUndefined();
    expect(inactivateRequest.request.body.mobilePhone).toBe(user.mobilePhone);
    inactivateRequest.flush({ ...user, status: 'I' });
    httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users` && request.method === 'GET'
    ).flush({
      content: [ { ...user, status: 'I' } ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true
    });

    fixture.detectChanges();
    const inactiveDialog = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement | null;
    expect(inactiveDialog).not.toBeNull();
    expect(inactiveDialog?.textContent).toContain('Activar');

    app.toggleEditingUserStatus();

    const activateRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users/admin/9`);
    expect(activateRequest.request.method).toBe('PUT');
    expect(activateRequest.request.body.status).toBe('A');
    expect(activateRequest.request.body.phone).toBeUndefined();
    expect(activateRequest.request.body.mobilePhone).toBe(user.mobilePhone);
    activateRequest.flush({ ...user, status: 'A' });
    httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users` && request.method === 'GET'
    ).flush({
      content: [ user ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true
    });
  });

  it('should reactivate an inactive user through an update', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    const user = {
      userId: 8,
      firstName: 'Nombre',
      paternalLastName: 'Apellido',
      maternalLastName: 'Materno',
      doi: '87654321',
      doiType: 'DNI',
      birthDate: '01/01/1990',
      gender: 'FEMALE',
      email: 'inactive@example.test',
      phone: '016543210',
      mobilePhone: '912345678',
      userType: 'STANDARD',
      profession: 'Profesión',
      status: 'I'
    };
    spyOn(window, 'confirm').and.returnValue(true);

    app.toggleStatus(user);

    const updateRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users/admin/8`);
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body).toEqual({ ...withoutPhone(user), status: 'A' });
    updateRequest.flush({ ...user, status: 'A' });
    const reloadRequest = httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users` && request.method === 'GET'
    );
    expect(reloadRequest.request.params.get('page')).toBe('0');
    expect(reloadRequest.request.params.get('size')).toBe('10');
    reloadRequest.flush({
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true
    });
  });

  it('should update the administrator password from the security form', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    const admin = {
      userId: 1,
      firstName: 'Admin',
      paternalLastName: 'Principal',
      maternalLastName: 'Sistema',
      doi: '12345678',
      doiType: 'DNI',
      birthDate: '01/01/1990',
      gender: 'OTHER',
      email: 'admin@example.test',
      phone: '016543210',
      mobilePhone: '912345678',
      userType: 'ADMIN',
      profession: 'Administrador',
      status: 'A'
    };
    app.adminProfile.set(admin);
    app.view.set('admin');
    app.adminSection.set('profile');
    app.profileForm.setValue({
      firstName: admin.firstName,
      paternalLastName: admin.paternalLastName,
      maternalLastName: admin.maternalLastName,
      birthDate: '1990-01-01',
      gender: admin.gender,
      email: admin.email,
      mobilePhone: admin.mobilePhone,
      profession: admin.profession
    });
    app.securityForm.setValue({ password: 'new-secret', confirmPassword: 'different-secret' });

    app.updateAdminPassword();
    expect(app.passwordMessage()).toBe('Las contraseñas no coinciden.');

    app.securityForm.controls.confirmPassword.setValue('new-secret');
    app.updateAdminPassword();

    const updateRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users/admin/1`);
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body).toEqual({ ...withoutPhone(admin), password: 'new-secret' });
    updateRequest.flush(admin);
    expect(app.securityForm.controls.password.value).toBe('');
    expect(app.securityForm.controls.confirmPassword.value).toBe('');
    expect(app.passwordSuccessModal()).toBeTrue();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('La contraseña se actualizó correctamente.');
  });

  it('should load the administrator profile when opening the profile section without cached data', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.accessForm.setValue({ doiType: 'DNI', doi: '12345678' });
    app.adminAuthenticated.set(true);
    app.view.set('admin');

    app.setAdminSection('profile');

    const profileRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users/doi/12345678`);
    expect(profileRequest.request.method).toBe('GET');
    profileRequest.flush({
      userId: 1,
      firstName: 'Admin',
      paternalLastName: 'Principal',
      maternalLastName: 'Sistema',
      doi: '12345678',
      doiType: 'DNI',
      birthDate: '01/01/1990',
      gender: 'OTHER',
      email: 'admin@example.test',
      phone: '',
      mobilePhone: '912345678',
      userType: 'ADMIN',
      profession: 'Administrador',
      status: 'A'
    });

    expect(app.currentAdmin()?.firstName).toBe('Admin');
    expect(app.profileForm.controls.firstName.value).toBe('Admin');
    fixture.detectChanges();
    const firstNameInput = fixture.nativeElement.querySelector(
      'app-admin-profile-section input[formControlName="firstName"]'
    ) as HTMLInputElement;
    expect(firstNameInput.value).toBe('Admin');
    expect(fixture.nativeElement.querySelector('app-admin-profile-section input[formControlName="phone"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-admin-profile-section input[formControlName="mobilePhone"]')).not.toBeNull();
  });

  it('should retain updated administrator profile data when reopening the profile section', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    const admin = {
      userId: 1,
      firstName: 'Admin',
      paternalLastName: 'Principal',
      maternalLastName: 'Sistema',
      doi: '12345678',
      doiType: 'DNI',
      birthDate: '01/01/1990',
      gender: 'OTHER',
      email: 'admin@example.test',
      phone: '',
      mobilePhone: '912345678',
      userType: 'ADMIN',
      profession: 'Administrador',
      status: 'A'
    };
    app.adminProfile.set(admin);
    app.view.set('admin');
    app.setAdminSection('profile');
    spyOn(app, 'loadUsers');
    app.profileForm.patchValue({ firstName: 'Admin actualizado' });

    app.updateProfile();

    const updateRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users/1`);
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body.firstName).toBe('Admin actualizado');
    expect(updateRequest.request.body.phone).toBeUndefined();
    updateRequest.flush({ ...admin, firstName: 'Admin actualizado' });

    expect(app.adminProfile()?.firstName).toBe('Admin actualizado');
    app.setAdminSection('users');
    app.setAdminSection('profile');
    expect(app.profileForm.controls.firstName.value).toBe('Admin actualizado');
  });

  it('should assign the default admin password when changing the edit form user type', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const user = {
      userId: 10,
      firstName: 'Usuario',
      paternalLastName: 'Estándar',
      maternalLastName: 'Prueba',
      doi: '11223344',
      doiType: 'DNI',
      birthDate: '01/01/1990',
      gender: 'FEMALE',
      email: 'standard@example.test',
      phone: '016543210',
      mobilePhone: '912345678',
      userType: 'STANDARD',
      profession: 'Profesional',
      status: 'A'
    };

    app.startEdit(user);
    app.changeEditingUserType({ target: { value: 'ADMIN' } } as unknown as Event);

    expect(app.editingUserType()).toBe('ADMIN');
    expect(app.userForm.controls.password.value).toBe('admin');
    expect(app.userForm.controls.password.valid).toBeTrue();
  });

  it('should edit an existing admin without exposing or updating their password', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    const admin = {
      userId: 11,
      firstName: 'Admin',
      paternalLastName: 'Principal',
      maternalLastName: 'Sistema',
      doi: '12345678',
      doiType: 'DNI',
      birthDate: '01/01/1990',
      gender: 'FEMALE',
      email: 'admin@example.test',
      phone: '016543210',
      mobilePhone: '912345678',
      userType: 'ADMIN',
      profession: 'Administrador',
      status: 'A'
    };

    app.view.set('admin');
    app.adminSection.set('users');
    app.startEdit(admin);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.modal-panel input[type="password"]')).toBeNull();
    expect(app.userForm.controls.password.valid).toBeTrue();
    expect(app.userForm.controls.doi.disabled).toBeTrue();
    app.userForm.controls.firstName.setValue('Admin actualizado');
    app.submit();

    const updateRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users/11`);
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body.password).toBeUndefined();
    expect(updateRequest.request.body.phone).toBeUndefined();
    updateRequest.flush(admin);
    httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users` && request.method === 'GET'
    ).flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true
    });
  });

  it('should toggle visibility of the administrator security password with its checkbox', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const admin = {
      userId: 11,
      firstName: 'Admin',
      paternalLastName: 'Principal',
      maternalLastName: 'Sistema',
      doi: '12345678',
      doiType: 'DNI',
      birthDate: '01/01/1990',
      gender: 'OTHER',
      email: 'admin@example.test',
      phone: '016543210',
      mobilePhone: '912345678',
      userType: 'ADMIN',
      profession: 'Administrador',
      status: 'A'
    };
    app.adminProfile.set(admin);
    app.view.set('admin');
    app.adminSection.set('profile');
    app.securityForm.controls.password.setValue('admin-secret');
    fixture.detectChanges();

    const passwordInput = fixture.nativeElement.querySelector('#profile-password') as HTMLInputElement;
    const visibilityCheckbox = fixture.nativeElement.querySelector('.terms-checkbox input[type="checkbox"]') as HTMLInputElement;
    expect(passwordInput.type).toBe('password');
    visibilityCheckbox.click();
    fixture.detectChanges();

    expect(passwordInput.type).toBe('text');
    expect(visibilityCheckbox.checked).toBeTrue();
  });
});
