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

  it('should render the home as the first screen', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Comprender lo que sientes');
    expect(compiled.textContent).not.toContain('Usuarios registrados');
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

  it('should show registration for an unknown document and register the standard user', () => {
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

    expect(fixture.nativeElement.textContent).toContain('Completa tu registro');
    expect(app.userForm.controls.doi.value).toBe('12345678');
    expect(app.userForm.controls.doiType.value).toBe('DNI');

    app.userForm.patchValue({
      firstName: 'Nombre de prueba',
      paternalLastName: 'Apellido paterno',
      maternalLastName: 'Apellido materno',
      birthDate: '01/01/1990',
      gender: 'FEMALE',
      email: 'standard.placeholder@example.test',
      phone: '016543210',
      mobilePhone: '912345678',
      profession: 'Profesión de prueba'
    });
    app.registerStandard();

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

    expect(app.view()).toBe('home');
    expect(app.accessMessage()).toContain('Registro completado');
  });

  it('should show the user CRUD after a successful admin login', () => {
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
    const usersRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users`);
    expect(usersRequest.request.headers.get('Authorization')).toBe('Bearer test-token');
    usersRequest.flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Usuarios registrados');
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
      password: ''
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
});
