import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { App } from './app';
import { AdminAnalysisService } from './services/admin-analysis.service';
import { AdminReportDisclaimerService } from './services/admin-report-disclaimer.service';
import { AdminProtectionGuideService } from './services/admin-protection-guide.service';
import { StandardAnalysisService } from './services/standard-analysis.service';
import { UserService } from './services/user.service';
import { environment } from '../environments/environment';

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

  it('should show only the latest disclaimer when entering settings', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.view.set('admin');
    app.setAdminSection('settings');
    fixture.detectChanges();

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

  it('should save disclaimer title and content together as a draft', () => {
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

    const root = fixture.nativeElement as HTMLElement;
    const titleInput = root.querySelector<HTMLInputElement>('input#disclaimer-title')!;
    const textarea = root.querySelector<HTMLTextAreaElement>('textarea#disclaimer-content')!;
    const saveButton = Array.from(root.querySelectorAll<HTMLButtonElement>('button'))
      .find(button => button.textContent?.includes('Guardar borrador'))!;
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
    expect(compiled.querySelector('h1')?.textContent)
      .toContain('Descubre cómo proteger la propiedad intelectual de tu creación');
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
      size: 20,
      totalElements: 42,
      totalPages: 3,
      first: true,
      last: false
    };

    app.loadUsers();
    httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users`
      && request.params.get('page') === '0'
      && request.params.get('size') === '20'
    ).flush(pageResponse);

    expect(app.users()).toEqual(pageResponse.content);
    expect(app.userTotalElements()).toBe(42);
    expect(app.userTotalPages()).toBe(3);

    app.changeUserPage(1);
    httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users`
      && request.params.get('page') === '1'
      && request.params.get('size') === '20'
    ).flush({ ...pageResponse, page: 1, first: false });

    expect(app.userPage()).toBe(1);
    expect(app.users()).toHaveSize(1);

    app.updateUserSearch({ target: { value: 'ana rojas' } } as unknown as Event);
    app.searchUsers();
    httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users`
      && request.params.get('page') === '0'
      && request.params.get('size') === '20'
      && request.params.get('search') === 'ana rojas'
    ).flush({ ...pageResponse, totalElements: 1, totalPages: 1 });

    expect(app.userPage()).toBe(0);
    expect(app.userTotalElements()).toBe(1);

    app.changeUserStatus({ target: { value: 'I' } } as unknown as Event);
    httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users`
      && request.params.get('page') === '0'
      && request.params.get('size') === '20'
      && request.params.get('search') === 'ana rojas'
      && request.params.get('status') === 'I'
    ).flush({ ...pageResponse, content: [], totalElements: 0, totalPages: 0 });

    expect(app.userStatus()).toBe('I');
    expect(app.userTotalElements()).toBe(0);

    app.changeUserType({ target: { value: 'ADMIN' } } as unknown as Event);
    httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users`
      && request.params.get('page') === '0'
      && request.params.get('size') === '20'
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
    await new Promise(resolve => setTimeout(resolve, 500));
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

  it('should show the analysis form and admin panel access after a successful admin login', async () => {
    const userService = TestBed.inject(UserService);
    userService.clearAccessToken();
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
    app.loginForm.setValue({ password: 'secret' });
    app.loginAdmin();

    httpTesting.expectOne(`${environment.apiBaseUrl}/users/login`).flush({
      tokenType: 'Bearer',
      accessToken: 'test-token'
    });
    await new Promise(resolve => setTimeout(resolve, 500));
    fixture.detectChanges();

    expect(app.view()).toBe('standard-analysis');
    expect(fixture.nativeElement.textContent).toContain('Cuéntanos qué quieres proteger');
    expect(fixture.nativeElement.textContent).toContain('Panel de administración');
    userService.clearAccessToken();
  });

  it('should show the predictions section without requesting the pending endpoint', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.view.set('admin');
    app.setAdminSection('predictions');
    fixture.detectChanges();

    httpTesting.expectNone(`${environment.apiBaseUrl}/admin/analyses`);
    expect(fixture.nativeElement.textContent).toContain('Historial de predicciones');
    expect(fixture.nativeElement.textContent).toContain('No hay predicciones registradas.');
  });

  it('should create only a standard user through the registration contract', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);
    app.view.set('registration');
    fixture.detectChanges();

    app.userForm.setValue({
      firstName: 'Nombre de prueba',
      paternalLastName: 'Apellido paterno',
      maternalLastName: 'Apellido materno',
      doi: '12345678',
      doiType: 'DNI',
      birthDate: '01/01/1990',
      gender: 'FEMALE',
      email: 'standard.placeholder@example.test',
      phone: '016543210',
      mobilePhone: '912345678',
      profession: 'Profesión de prueba',
      password: '',
      termsAccepted: true
    });

    fixture.detectChanges();
    const createButton = fixture.nativeElement.querySelector(
      '.registration-actions button[type="submit"]'
    ) as HTMLButtonElement;
    createButton.click();

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
      phone: '016543210',
      mobilePhone: '912345678',
      profession: 'Profesión de prueba'
    });
    registrationRequest.flush({ registered: true });
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
    expect(updateRequest.request.body).toEqual({ ...user, status: 'I' });
    httpTesting.expectNone(`${environment.apiBaseUrl}/users/7`);
    updateRequest.flush({ ...user, status: 'I' });
    const reloadRequest = httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users` && request.method === 'GET'
    );
    expect(reloadRequest.request.params.get('page')).toBe('0');
    expect(reloadRequest.request.params.get('size')).toBe('20');
    reloadRequest.flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true
    });
  });

  it('should delete the edited user and reload the user list', () => {
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
    app.startEdit(user);

    app.deleteEditingUser();

    const deleteRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users/9`);
    expect(deleteRequest.request.method).toBe('DELETE');
    deleteRequest.flush(null, { status: 204, statusText: 'No Content' });

    expect(app.showEditModal()).toBeFalse();
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
    expect(updateRequest.request.body).toEqual({ ...user, status: 'A' });
    updateRequest.flush({ ...user, status: 'A' });
    const reloadRequest = httpTesting.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/users` && request.method === 'GET'
    );
    expect(reloadRequest.request.params.get('page')).toBe('0');
    expect(reloadRequest.request.params.get('size')).toBe('20');
    reloadRequest.flush({
      content: [],
      page: 0,
      size: 20,
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
      phone: admin.phone,
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
    expect(updateRequest.request.body).toEqual({ ...admin, password: 'new-secret' });
    updateRequest.flush(admin);
    expect(app.securityForm.controls.password.value).toBe('');
    expect(app.securityForm.controls.confirmPassword.value).toBe('');
    expect(app.passwordSuccessModal()).toBeTrue();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('La contraseña se actualizó correctamente.');
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
