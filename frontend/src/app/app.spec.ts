import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { App } from './app';
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

  it('should remove non-numeric characters from a DNI during registration', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const input = document.createElement('input');
    input.value = '12AB3456789';

    app.sanitizeDni({ target: input } as unknown as Event, app.userForm.controls.doi);

    expect(input.value).toBe('12345678');
    expect(app.userForm.controls.doi.value).toBe('12345678');
  });

  it('should render the home as the first screen', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Comprender lo que sientes');
    expect(compiled.textContent).not.toContain('Usuarios registrados');
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

  it('should request a password automatically when the document belongs to an admin', () => {
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

  it('should show a message for an unknown document without opening registration', () => {
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
    fixture.detectChanges();

    expect(app.view()).toBe('standard-analysis');
    expect(fixture.nativeElement.textContent).toContain('Cuéntanos qué quieres proteger');
    expect(fixture.nativeElement.textContent).toContain('Panel de administración');
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
    app.view.set('admin');
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
      termsAccepted: false
    });

    fixture.detectChanges();
  const createButton = fixture.nativeElement.querySelector('.actions button') as HTMLButtonElement;
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
    httpTesting.expectOne(`${environment.apiBaseUrl}/users`).flush([]);
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
    httpTesting.expectOne(`${environment.apiBaseUrl}/users`).flush([]);
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
    httpTesting.expectOne(`${environment.apiBaseUrl}/users`).flush([]);
  });

  it('should update the authenticated administrator password', () => {
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
    app.accessForm.setValue({ doi: admin.doi, doiType: admin.doiType });
    app.users.set([admin]);
    app.passwordForm.setValue({ newPassword: 'new-secret', confirmPassword: 'new-secret' });

    app.changeAdminPassword();

    const updateRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users/admin/1`);
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body).toEqual({ ...admin, password: 'new-secret' });
    updateRequest.flush(admin);
    expect(app.passwordMessage()).toBe('Contraseña actualizada correctamente.');
    expect(app.passwordForm.getRawValue()).toEqual({ newPassword: '', confirmPassword: '' });
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

  it('should toggle visibility of the admin password in the edit form', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const user = {
      userId: 11,
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
    fixture.detectChanges();

    const passwordInput = fixture.nativeElement.querySelector('[formControlName="password"]') as HTMLInputElement;
    const visibilityButton = fixture.nativeElement.querySelector('.password-visibility') as HTMLButtonElement;
    expect(passwordInput.type).toBe('password');
    visibilityButton.click();
    fixture.detectChanges();

    expect(passwordInput.type).toBe('text');
    expect(visibilityButton.getAttribute('aria-pressed')).toBe('true');
  });
});
