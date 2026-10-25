import { CommonModule } from '@angular/common';
import { DOCUMENT } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { StandardUserRegistrationRequest, UserService } from './services/user.service';
import { User } from './models/user.model';
import { AdminAnalysis, AnalysisStatus } from './services/admin-analysis.service';
import { AnalysisResponse, StandardAnalysisService } from './services/standard-analysis.service';

@Component({
  selector: 'app-root',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private readonly fb = inject(FormBuilder);
  private readonly document = inject(DOCUMENT);
  private readonly userService = inject(UserService);
  private readonly standardAnalysisService = inject(StandardAnalysisService);
  private readonly defaultAdminPassword = 'admin';

  readonly users = signal<User[]>([]);
  readonly userPage = signal(0);
  readonly userPageSize = signal(20);
  readonly userSearch = signal('');
  readonly userStatus = signal('');
  readonly userType = signal('');
  readonly userTotalPages = signal(0);
  readonly userTotalElements = signal(0);
  readonly downloadingUsers = signal(false);
  readonly analyses = signal<AdminAnalysis[]>([]);
  readonly loading = signal(false);
  readonly analysesLoading = signal(false);
  readonly saving = signal(false);
  readonly registrationModal = signal<'registering' | 'success' | null>(null);
  readonly errorMessage = signal('');
  readonly analysesError = signal('');
  readonly editingUserId = signal<number | null>(null);
  readonly editingUserType = signal('STANDARD');
  readonly showAdminPassword = signal(false);
  readonly showEditModal = signal(false);
  readonly adminSection = signal<'users' | 'predictions' | 'profile'>('users');
  readonly view = signal<'home' | 'registration' | 'standard-analysis' | 'standard-result' | 'admin-login' | 'admin' | 'legal'>('home');
  readonly checkingDocument = signal(false);
  readonly standardAccessPending = signal(false);
  readonly authenticating = signal(false);
  readonly adminAuthenticated = signal(false);
  readonly navigationPending = signal<'admin' | 'predictions' | 'exit' | null>(null);
  readonly accessMessage = signal('');
  readonly savingProfile = signal(false);
  readonly profileMessage = signal('');
  readonly adminProfile = signal<User | null>(null);
  readonly submittingAnalysis = signal(false);
  readonly analysisMessage = signal('');
  readonly analysisResult = signal<AnalysisResponse | null>(null);
  readonly selectedPdf = signal<File | null>(null);

  constructor() {
    if (this.document.location.pathname === '/terminos-y-privacidad') {
      this.view.set('legal');
    }
    this.accessForm.controls.doiType.valueChanges.subscribe(() => this.accessForm.controls.doi.updateValueAndValidity());
    this.userForm.controls.doiType.valueChanges.subscribe(() => this.userForm.controls.doi.updateValueAndValidity());

    const adminIdentity = this.userService.getAdminIdentity();
    if (adminIdentity) {
      this.accessForm.setValue(adminIdentity);
      this.adminAuthenticated.set(true);
      this.adminSection.set('users');
      this.view.set('admin');
      this.userService.findByDoi(adminIdentity.doi).subscribe({
        next: admin => {
          if (admin.userType?.toUpperCase() !== 'ADMIN' || admin.doiType !== adminIdentity.doiType) {
            this.logout();
            return;
          }

          this.adminProfile.set(admin);
          if (this.adminSection() === 'profile') {
            this.loadProfileForm();
          }
          this.loadUsers(0);
        },
        error: () => this.logout()
      });
    }
  }

  readonly currentAdmin = computed<User | null>(() => {
    const authenticatedAdmin = this.adminProfile();
    if (authenticatedAdmin) {
      return authenticatedAdmin;
    }

    const { doi, doiType } = this.accessForm.getRawValue();
    return this.users().find(user =>
      user.userType?.toUpperCase() === 'ADMIN'
      && user.doi === doi
      && user.doiType === doiType
    ) ?? null;
  });

  readonly accessForm = this.fb.nonNullable.group({
    doiType: ['DNI', [Validators.required]],
    doi: ['', [Validators.required, this.documentValidator.bind(this)]]
  });

  readonly loginForm = this.fb.nonNullable.group({
    password: ['', [Validators.required]]
  });

  readonly userForm = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(25)]],
    paternalLastName: ['', [Validators.required, Validators.maxLength(25)]],
    maternalLastName: ['', [Validators.required, Validators.maxLength(25)]],
    doi: ['', [Validators.required, this.documentValidator.bind(this)]],
    doiType: ['', [Validators.required]],
    birthDate: ['', [Validators.required, this.dateValidator]],
    gender: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', [Validators.required, Validators.maxLength(9), Validators.pattern(/^\d+$/)]],
    mobilePhone: ['', [Validators.required, Validators.maxLength(15), this.mobilePhoneValidator]],
    profession: ['', [Validators.required]],
    password: [''],
    termsAccepted: [false, [Validators.requiredTrue]]
  });

  readonly profileForm = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(25)]],
    paternalLastName: ['', [Validators.required, Validators.maxLength(25)]],
    maternalLastName: ['', [Validators.required, Validators.maxLength(25)]],
    birthDate: ['', [Validators.required, this.dateValidator]],
    gender: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', [Validators.required, Validators.maxLength(9), Validators.pattern(/^\d+$/)]],
    mobilePhone: ['', [Validators.required, Validators.maxLength(15), this.mobilePhoneValidator]],
    profession: ['', [Validators.required]]
  });

  readonly analysisForm = this.fb.nonNullable.group({
    description: ['', [Validators.required, Validators.minLength(20), Validators.maxLength(20000)]]
  });

  private documentValidator(control: AbstractControl): ValidationErrors | null {
    if (!control.value) {
      return null;
    }

    const doiType = control.parent?.get('doiType')?.value;
    const pattern = doiType === 'DNI' ? /^\d{8}$/ : /^\d{6,11}$/;
    return pattern.test(control.value) ? null : { doiFormat: true };
  }

  private dateValidator(control: AbstractControl): ValidationErrors | null {
    if (!control.value) {
      return null;
    }

    return /^(?:\d{4}-\d{2}-\d{2}|\d{2}\/\d{2}\/\d{4})$/.test(control.value)
      ? null
      : { dateFormat: true };
  }

  private mobilePhoneValidator(control: AbstractControl): ValidationErrors | null {
    if (!control.value) {
      return null;
    }

    const normalized = String(control.value).replace(/\s/g, '');
    const isValid = /^\d{9}$/.test(normalized)
      || /^\+51\d{9}$/.test(normalized);
    return isValid ? null : { mobilePhoneFormat: true };
  }

  sanitizeMobilePhone(event: Event, control: AbstractControl): void {
    const input = event.target as HTMLInputElement;
    const hasLeadingPlus = input.value.trimStart().startsWith('+');
    const sanitized = `${hasLeadingPlus ? '+' : ''}${input.value
      .replace(/\+/g, '')
      .replace(/[^\d ]/g, '')
      .trimStart()}`;

    if (input.value !== sanitized) {
      input.value = sanitized;
      control.setValue(sanitized, { emitEvent: false });
    }
  }

  sanitizePhone(event: Event, control: AbstractControl): void {
    const input = event.target as HTMLInputElement;
    const sanitized = input.value.replace(/\D/g, '').slice(0, 9);
    if (input.value !== sanitized) {
      input.value = sanitized;
      control.setValue(sanitized, { emitEvent: false });
    }
  }

  sanitizeDni(event: Event, control: AbstractControl): void {
    const input = event.target as HTMLInputElement;
    const sanitized = input.value.replace(/\D/g, '').slice(0, 8);
    if (input.value !== sanitized) {
      input.value = sanitized;
      control.setValue(sanitized, { emitEvent: false });
    }
  }

  private toDateInputValue(date: string): string {
    const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(date);
    return match ? `${match[3]}-${match[2]}-${match[1]}` : date.slice(0, 10);
  }

  private toBackendDate(date: string): string {
    const isoMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
    return isoMatch ? `${isoMatch[3]}/${isoMatch[2]}/${isoMatch[1]}` : date;
  }

  identifyDocument(): void {
    if (this.accessForm.invalid) {
      this.accessForm.markAllAsTouched();
      return;
    }

    const { doi, doiType } = this.accessForm.getRawValue();
    const accessStartedAt = Date.now();
    this.checkingDocument.set(true);
    this.standardAccessPending.set(false);
    this.accessMessage.set('');
    this.userService.identify(doi, doiType).subscribe({
      next: ({ registered, passwordRequired, active }) => {
        this.standardAccessPending.set(registered && !passwordRequired);
        this.finishAccess(accessStartedAt, () => {
          if (registered && active === false) {
            this.accessMessage.set('Usuario inactivo');
          } else if (passwordRequired) {
            this.view.set('admin-login');
            this.loginForm.reset({ password: '' });
          } else if (!registered) {
            this.accessMessage.set('Usuario no registrado');
          } else {
            this.analysisForm.reset({ description: '' });
            this.analysisMessage.set('');
            this.view.set('standard-analysis');
            this.scrollToTop();
          }
          this.standardAccessPending.set(false);
          this.checkingDocument.set(false);
        });
      },
      error: () => {
        this.finishAccess(accessStartedAt, () => {
          this.standardAccessPending.set(false);
          this.accessMessage.set('No pudimos verificar el documento. Inténtalo nuevamente.');
          this.checkingDocument.set(false);
        });
      }
    });
  }

  private finishAccess(startedAt: number, complete: () => void): void {
    const remainingMinimumTime = Math.max(0, 500 - (Date.now() - startedAt));
    setTimeout(complete, remainingMinimumTime);
  }

  private scrollToTop(): void {
    this.document.defaultView?.scrollTo({ top: 0, left: 0, behavior: 'auto' });
  }

  backHome(): void {
    this.view.set('home');
    this.accessMessage.set('');
    this.loginForm.reset({ password: '' });
  }

  exitSession(): void {
    this.accessForm.reset({ doiType: 'DNI', doi: '' });
    this.analysisForm.reset({ description: '' });
    this.analysisMessage.set('');
    this.analysisResult.set(null);
    this.backHome();
  }

  exitAnalysis(): void {
    this.scheduleNavigation('exit', () => this.backHome());
  }

  exitResult(): void {
    this.scheduleNavigation('exit', () => this.exitSession());
  }

  openRegistration(): void {
    this.cancelEdit();
    this.accessMessage.set('');
    this.view.set('registration');
  }

  startNewAnalysis(): void {
    this.analysisForm.reset({ description: '' });
    this.analysisMessage.set('');
    this.analysisResult.set(null);
    this.view.set('standard-analysis');
    this.scrollToTop();
  }

  cancelRegistration(): void {
    this.cancelEdit();
    this.backHome();
  }

  loginAdmin(): void {
    if (this.accessForm.invalid || this.loginForm.invalid) {
      this.accessForm.markAllAsTouched();
      this.loginForm.markAllAsTouched();
      return;
    }

    const { doi } = this.accessForm.getRawValue();
    const { password } = this.loginForm.getRawValue();
    const accessStartedAt = Date.now();
    this.authenticating.set(true);
    this.accessMessage.set('');
    this.userService.login(doi, password).subscribe({
      next: ({ accessToken }) => {
        this.finishAccess(accessStartedAt, () => {
          const { doiType } = this.accessForm.getRawValue();
          this.userService.setAccessToken(accessToken, doi, doiType);
          this.authenticating.set(false);
          this.adminAuthenticated.set(true);
          this.analysisForm.reset({ description: '' });
          this.analysisMessage.set('');
          this.view.set('standard-analysis');
          this.scrollToTop();
        });
      },
      error: () => {
        this.finishAccess(accessStartedAt, () => {
          this.accessMessage.set('Documento o contraseña incorrectos.');
          this.authenticating.set(false);
        });
      }
    });
  }

  logout(): void {
    this.userService.clearAccessToken();
    this.adminAuthenticated.set(false);
    this.adminProfile.set(null);
    this.users.set([]);
    this.analyses.set([]);
    this.cancelEdit();
    this.adminSection.set('users');
    this.profileMessage.set('');
    this.backHome();
  }

  openAdminPanel(): void {
    if (this.navigationPending()) {
      return;
    }

    this.navigationPending.set('admin');
    window.setTimeout(() => {
      this.navigationPending.set(null);
      this.adminSection.set('users');
      this.view.set('admin');
      this.loadUsers(0);
      this.loadAdminProfile();
      this.scrollToTop();
    }, 500);
  }

  openPredictions(): void {
    this.scheduleNavigation('predictions', () => this.startNewAnalysis());
  }

  private scheduleNavigation(
    destination: 'admin' | 'predictions' | 'exit',
    navigate: () => void
  ): void {
    if (this.navigationPending()) {
      return;
    }

    this.navigationPending.set(destination);
    window.setTimeout(() => {
      this.navigationPending.set(null);
      navigate();
    }, 500);
  }

  setAdminSection(section: 'users' | 'predictions' | 'profile'): void {
    this.adminSection.set(section);
    if (section === 'predictions') {
      this.loadAnalyses();
    } else if (section === 'profile') {
      this.loadProfileForm();
    }
  }

  private loadAdminProfile(): void {
    const { doi, doiType } = this.accessForm.getRawValue();
    this.userService.findByDoi(doi).subscribe({
      next: admin => {
        if (admin.userType?.toUpperCase() !== 'ADMIN' || admin.doiType !== doiType) {
          return;
        }

        this.adminProfile.set(admin);
        if (this.adminSection() === 'profile') {
          this.loadProfileForm();
        }
      }
    });
  }

  loadAnalyses(): void {
    this.analysesError.set('');
  }

  downloadAnalysesCsv(): void {
    const headers = ['Fecha', 'Usuario', 'Estado', 'Predicción'];
    const rows = this.analyses().map(analysis => [
      analysis.requestedAt,
      `${analysis.firstName} ${analysis.paternalLastName} ${analysis.maternalLastName} (Usuario #${analysis.userId})`,
      this.analysisStatusLabel(analysis.status),
      analysis.summary || 'Resultado aún no disponible.'
    ]);
    const csv = '\uFEFF' + [headers, ...rows]
      .map(row => row.map(value => this.csvCell(value)).join(','))
      .join('\r\n');
    const link = this.document.createElement('a');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));

    link.href = url;
    link.download = `predicciones-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  downloadUsersCsv(): void {
    this.downloadingUsers.set(true);
    this.errorMessage.set('');
    this.userService.exportAll(this.userSearch(), this.userStatus(), this.userType()).subscribe({
      next: response => {
        const blob = response.body;
        if (!blob) {
          this.errorMessage.set('No se pudo descargar el archivo de usuarios.');
          this.downloadingUsers.set(false);
          return;
        }

        const contentDisposition = response.headers.get('Content-Disposition');
        const filename = contentDisposition?.match(/filename="?([^";]+)"?/i)?.[1]
          ?? `usuarios-${new Date().toISOString().slice(0, 10)}.csv`;
        const url = URL.createObjectURL(blob);
        const link = this.document.createElement('a');
        link.href = url;
        link.download = filename;
        link.click();
        window.setTimeout(() => URL.revokeObjectURL(url));
        this.downloadingUsers.set(false);
      },
      error: () => {
        this.errorMessage.set('No se pudieron exportar los usuarios.');
        this.downloadingUsers.set(false);
      }
    });
  }

  private csvCell(value: string): string {
    const safeValue = /^[=+\-@]/.test(value) ? `'${value}` : value;
    return `"${safeValue.replace(/"/g, '""')}"`;
  }

  analysisStatusLabel(status: AnalysisStatus): string {
    const labels: Record<AnalysisStatus, string> = {
      RECEIVED: 'Recibida',
      PREPROCESSING: 'Procesando',
      ANALYZING: 'Analizando',
      COMPLETED: 'Completada',
      FAILED: 'Fallida'
    };
    return labels[status];
  }

  loadUsers(page = this.userPage()): void {
    this.loading.set(true);
    this.errorMessage.set('');

    this.userService.findAll(page, this.userPageSize(), this.userSearch(), this.userStatus(), this.userType()).subscribe({
      next: (response) => {
        this.users.set(response.content);
        this.userPage.set(response.page);
        this.userTotalPages.set(response.totalPages);
        this.userTotalElements.set(response.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('No se pudieron cargar los usuarios.');
        this.loading.set(false);
      }
    });
  }

  changeUserPage(page: number): void {
    if (this.loading() || page < 0 || page >= this.userTotalPages()) {
      return;
    }

    this.loadUsers(page);
  }

  changeUserPageSize(event: Event): void {
    const size = Number((event.target as HTMLSelectElement).value);
    if (![10, 20, 50, 100].includes(size)) {
      return;
    }

    this.userPageSize.set(size);
    this.loadUsers(0);
  }

  updateUserSearch(event: Event): void {
    this.userSearch.set((event.target as HTMLInputElement).value);
  }

  searchUsers(): void {
    this.loadUsers(0);
  }

  changeEditingUserType(event: Event): void {
    const userType = (event.target as HTMLSelectElement).value;
    if (!['STANDARD', 'ADMIN'].includes(userType)) {
      return;
    }

    const wasAdmin = this.editingUserType() === 'ADMIN';
    this.editingUserType.set(userType);
    const passwordControl = this.userForm.controls.password;
    if (userType === 'ADMIN') {
      if (!wasAdmin) {
        passwordControl.setValue(this.defaultAdminPassword);
      }
      passwordControl.setValidators([Validators.required]);
    } else {
      passwordControl.setValue('');
      passwordControl.clearValidators();
    }
    passwordControl.updateValueAndValidity({ emitEvent: false });
  }

  toggleAdminPasswordVisibility(): void {
    this.showAdminPassword.update(visible => !visible);
  }

  changeUserStatus(event: Event): void {
    const status = (event.target as HTMLSelectElement).value;
    if (!['', 'A', 'I'].includes(status)) {
      return;
    }

    this.userStatus.set(status);
    this.loadUsers(0);
  }

  changeUserType(event: Event): void {
    const userType = (event.target as HTMLSelectElement).value;
    if (!['', 'STANDARD', 'ADMIN'].includes(userType)) {
      return;
    }

    this.userType.set(userType);
    this.loadUsers(0);
  }

  registerStandard(): void {
    if (this.userForm.invalid) {
      this.userForm.markAllAsTouched();
      this.accessMessage.set('Completa correctamente todos los campos obligatorios.');
      return;
    }

    this.saving.set(true);
    const registrationStartedAt = Date.now();
    this.accessMessage.set('');
    this.userService.create(this.registrationPayload()).subscribe({
      next: () => {
        this.saving.set(false);
        const remainingMinimumTime = Math.max(0, 500 - (Date.now() - registrationStartedAt));
        setTimeout(() => {
          this.registrationModal.set('success');
          setTimeout(() => {
            this.registrationModal.set(null);
            this.cancelEdit();
            this.analysisForm.reset({ description: '' });
            this.analysisMessage.set('');
            this.view.set('standard-analysis');
            this.scrollToTop();
          }, 2000);
        }, remainingMinimumTime);
      },
      error: () => {
        this.saving.set(false);
        this.registrationModal.set(null);
        this.accessMessage.set('No se pudo completar el registro. Verifica los datos e inténtalo nuevamente.');
      }
    });
  }

  submitAnalysis(): void {
    const selectedPdf = this.selectedPdf();
    if (!selectedPdf && this.analysisForm.invalid) {
      this.analysisForm.markAllAsTouched();
      return;
    }

    const { doi, doiType } = this.accessForm.getRawValue();
    const { description } = this.analysisForm.getRawValue();
    this.submittingAnalysis.set(true);
    this.analysisMessage.set('');
    this.analysisResult.set(null);

    const adminAccessToken = this.adminAuthenticated() ? this.userService.getAccessToken() : '';
    const request = selectedPdf
      ? this.standardAnalysisService.createPdfAnalysis(doi, doiType, selectedPdf, adminAccessToken)
      : this.standardAnalysisService.createTextAnalysis(doi, doiType, description, adminAccessToken);
    request.subscribe({
      next: ({ analysisId }) => {
        this.selectedPdf.set(null);
        this.analysisMessage.set('');
        this.pollAnalysis(analysisId, adminAccessToken);
      },
      error: () => {
        this.submittingAnalysis.set(false);
        this.analysisMessage.set('No se pudo iniciar el análisis. Verifica tu sesión, consentimiento e intenta nuevamente.');
      }
    });
  }

  onPdfSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';

    if (!file) {
      return;
    }

    if (file.type !== 'application/pdf') {
      this.analysisMessage.set('Selecciona un archivo PDF válido.');
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      this.analysisMessage.set('El PDF no puede superar los 10 MiB.');
      return;
    }

    this.selectedPdf.set(file);
    this.analysisForm.controls.description.setValue('');
    this.analysisForm.controls.description.markAsUntouched();
    this.analysisMessage.set('');
  }

  removeSelectedPdf(): void {
    this.selectedPdf.set(null);
    this.analysisForm.controls.description.setValue('');
    this.analysisForm.controls.description.markAsUntouched();
    this.analysisMessage.set('');
  }

  private downloadAnalysisJson(analysisId: string, analysis: unknown): void {
    const link = this.document.createElement('a');
    const url = URL.createObjectURL(new Blob([
      JSON.stringify(analysis, null, 2)
    ], { type: 'application/json;charset=utf-8' }));

    link.href = url;
    link.download = `analysis-${analysisId}.json`;
    link.click();
    URL.revokeObjectURL(url);
  }

  private pollAnalysis(analysisId: string, accessToken = ''): void {
    this.standardAnalysisService.getAnalysis(analysisId, accessToken).subscribe({
      next: (analysis) => {
        this.analysisResult.set(analysis);
        console.log('JSON del análisis:', analysis);
        if (analysis.status === 'COMPLETED' || analysis.status === 'FAILED') {
          this.submittingAnalysis.set(false);
          this.analysisMessage.set(analysis.status === 'COMPLETED'
            ? ''
            : 'El análisis no pudo completarse.');
          this.downloadAnalysisJson(analysisId, analysis);
          this.view.set('standard-result');
          this.scrollToTop();
          return;
        }

        window.setTimeout(() => this.pollAnalysis(analysisId, accessToken), 2000);
      },
      error: (error) => {
        this.submittingAnalysis.set(false);
        this.analysisMessage.set('No se pudo consultar el resultado del análisis.');
        console.error('No se pudo consultar el análisis:', error);
      }
    });
  }

  protectionTypeLabel(type: string): string {
    const labels: Record<string, string> = {
      INVENTION_PATENT: 'Patente de invención',
      UTILITY_MODEL: 'Modelo de utilidad',
      INDUSTRIAL_DESIGN: 'Diseño industrial',
      DISTINCTIVE_SIGN: 'Signo distintivo',
      COPYRIGHT: 'Derechos de autor',
      OTHER: 'Otra modalidad'
    };
    return labels[type] ?? type;
  }

  applicabilityLabel(applicability: string): string {
    const labels: Record<string, string> = {
      POSSIBLE: 'Posible',
      LIKELY: 'Probable',
      UNLIKELY: 'Poco probable',
      NO_POTENTIAL_MATCH: 'Sin coincidencia potencial',
      INSUFFICIENT_INFORMATION: 'Información insuficiente'
    };
    return labels[applicability] ?? applicability;
  }

  submit(): void {
    const userId = this.editingUserId();
    if (userId === null) {
      return;
    }

    if (this.userForm.invalid) {
      this.userForm.markAllAsTouched();
      this.errorMessage.set('Completa correctamente todos los campos obligatorios.');
      return;
    }

    const formValue = this.userForm.getRawValue();
    const registrationPayload = this.registrationPayload();

    this.saving.set(true);
    this.errorMessage.set('');

    const request$: Observable<unknown> = this.userService.update(userId, {
      ...registrationPayload,
      userType: this.editingUserType(),
      password: formValue.password || undefined
    });

    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.cancelEdit();
        this.loadUsers();
      },
      error: () => {
        this.errorMessage.set('No se pudo guardar el usuario.');
        this.saving.set(false);
      }
    });
  }

  startEdit(user: User): void {
    if (!user.userId) {
      return;
    }

    this.editingUserId.set(user.userId);
    this.editingUserType.set(user.userType);
    this.showAdminPassword.set(false);
    const passwordControl = this.userForm.controls.password;
    if (user.userType === 'ADMIN') {
      passwordControl.setValidators([Validators.required]);
    } else {
      passwordControl.clearValidators();
    }
    passwordControl.updateValueAndValidity({ emitEvent: false });
    this.userForm.setValue({
      firstName: user.firstName,
      paternalLastName: user.paternalLastName,
      maternalLastName: user.maternalLastName,
      doi: user.doi,
      doiType: user.doiType,
      birthDate: this.toDateInputValue(user.birthDate),
      gender: user.gender,
      email: user.email,
      phone: user.phone,
      mobilePhone: user.mobilePhone,
      profession: user.profession,
      password: '',
      termsAccepted: true
    });
    this.showEditModal.set(true);
  }

  cancelEdit(): void {
    this.editingUserId.set(null);
    this.editingUserType.set('STANDARD');
    this.showAdminPassword.set(false);
    this.showEditModal.set(false);
    this.userForm.controls.password.clearValidators();
    this.userForm.controls.password.updateValueAndValidity({ emitEvent: false });
    this.userForm.reset({
      firstName: '',
      paternalLastName: '',
      maternalLastName: '',
      doi: '',
      doiType: '',
      birthDate: '',
      gender: '',
      email: '',
      phone: '',
      mobilePhone: '',
      profession: '',
      password: '',
      termsAccepted: false
    });
  }

  toggleStatus(user: User): void {
    if (!user.userId) {
      return;
    }

    const activating = user.status?.toUpperCase() === 'I';
    const action = activating ? 'activar' : 'inactivar';
    const accepted = confirm(`¿Seguro que quieres ${action} al usuario #${user.userId}?`);
    if (!accepted) {
      return;
    }

    this.errorMessage.set('');
    this.userService.update(user.userId, { ...user, status: activating ? 'A' : 'I' }).subscribe({
      next: () => this.loadUsers(),
      error: () => this.errorMessage.set(`No se pudo ${action} el usuario.`)
    });
  }

  isStandard(user: User): boolean {
    return user.userType?.toUpperCase() === 'STANDARD';
  }

  isInactive(user: User): boolean {
    return user.status?.toUpperCase() === 'I';
  }

  isEditing(user: User): boolean {
    return this.editingUserId() === user.userId;
  }

  private loadProfileForm(): void {
    this.profileMessage.set('');
    const admin = this.currentAdmin();
    if (!admin) {
      return;
    }

    this.profileForm.setValue({
      firstName: admin.firstName,
      paternalLastName: admin.paternalLastName,
      maternalLastName: admin.maternalLastName,
      birthDate: this.toDateInputValue(admin.birthDate),
      gender: admin.gender,
      email: admin.email,
      phone: admin.phone,
      mobilePhone: admin.mobilePhone,
      profession: admin.profession
    });
  }

  updateProfile(): void {
    const admin = this.currentAdmin();
    if (!admin?.userId) {
      this.profileMessage.set('No se pudo identificar al administrador autenticado.');
      return;
    }

    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      this.profileMessage.set('Completa correctamente todos los campos obligatorios.');
      return;
    }

    this.savingProfile.set(true);
    this.profileMessage.set('');
    this.userService.updateProfile(admin.userId, {
      userId: admin.userId,
      ...admin,
      ...this.profileForm.getRawValue(),
      birthDate: this.toBackendDate(this.profileForm.controls.birthDate.value),
      doi: admin.doi,
      doiType: admin.doiType,
      userType: admin.userType,
      status: admin.status
    }).subscribe({
      next: () => {
        this.savingProfile.set(false);
        this.profileMessage.set('Tus datos se actualizaron correctamente.');
        this.loadUsers();
      },
      error: () => {
        this.savingProfile.set(false);
        this.profileMessage.set('No se pudo actualizar tu información.');
      }
    });
  }

  private registrationPayload(): StandardUserRegistrationRequest {
    const formValue = this.userForm.getRawValue();
    return {
      firstName: formValue.firstName,
      paternalLastName: formValue.paternalLastName,
      maternalLastName: formValue.maternalLastName,
      doi: formValue.doi,
      doiType: formValue.doiType,
      birthDate: this.toBackendDate(formValue.birthDate),
      gender: formValue.gender,
      email: formValue.email,
      phone: formValue.phone,
      mobilePhone: formValue.mobilePhone,
      profession: formValue.profession
    };
  }
}
