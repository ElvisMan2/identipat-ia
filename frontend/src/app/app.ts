import { CommonModule } from '@angular/common';
import { DOCUMENT } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { StandardUserRegistrationRequest, UserService } from './services/user.service';
import { User } from './models/user.model';
import { AdminAnalysis, AnalysisStatus } from './services/admin-analysis.service';

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

  readonly users = signal<User[]>([]);
  readonly analyses = signal<AdminAnalysis[]>([]);
  readonly loading = signal(false);
  readonly analysesLoading = signal(false);
  readonly saving = signal(false);
  readonly errorMessage = signal('');
  readonly analysesError = signal('');
  readonly editingUserId = signal<number | null>(null);
  readonly editingUserType = signal('STANDARD');
  readonly showEditModal = signal(false);
  readonly adminSection = signal<'users' | 'predictions' | 'profile'>('users');
  readonly view = signal<'home' | 'registration' | 'standard-analysis' | 'admin-login' | 'admin'>('home');
  readonly checkingDocument = signal(false);
  readonly authenticating = signal(false);
  readonly accessMessage = signal('');
  readonly savingProfile = signal(false);
  readonly profileMessage = signal('');

  readonly currentAdmin = computed<User | null>(() => {
    const { doi, doiType } = this.accessForm.getRawValue();
    return this.users().find(user =>
      user.userType?.toUpperCase() === 'ADMIN'
      && user.doi === doi
      && user.doiType === doiType
    ) ?? null;
  });

  readonly accessForm = this.fb.nonNullable.group({
    doiType: ['DNI', [Validators.required]],
    doi: ['', [Validators.required, Validators.pattern(/^\d{8,12}$/)]]
  });

  readonly loginForm = this.fb.nonNullable.group({
    password: ['', [Validators.required]]
  });

  readonly userForm = this.fb.nonNullable.group({
    firstName: ['', [Validators.required]],
    paternalLastName: ['', [Validators.required]],
    maternalLastName: ['', [Validators.required]],
    doi: ['', [Validators.required]],
    doiType: ['', [Validators.required]],
    birthDate: ['', [Validators.required, Validators.pattern(/^\d{2}\/\d{2}\/\d{4}$/)]],
    gender: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', [Validators.required]],
    mobilePhone: ['', [Validators.required]],
    profession: ['', [Validators.required]],
    password: ['']
  });

  readonly profileForm = this.fb.nonNullable.group({
    firstName: ['', [Validators.required]],
    paternalLastName: ['', [Validators.required]],
    maternalLastName: ['', [Validators.required]],
    birthDate: ['', [Validators.required, Validators.pattern(/^\d{2}\/\d{2}\/\d{4}$/)]],
    gender: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', [Validators.required]],
    mobilePhone: ['', [Validators.required]],
    profession: ['', [Validators.required]]
  });

  readonly analysisForm = this.fb.nonNullable.group({
    description: ['']
  });

  identifyDocument(): void {
    if (this.accessForm.invalid) {
      this.accessForm.markAllAsTouched();
      return;
    }

    const { doi, doiType } = this.accessForm.getRawValue();
    this.checkingDocument.set(true);
    this.accessMessage.set('');
    this.userService.identify(doi, doiType).subscribe({
      next: ({ registered, passwordRequired }) => {
        if (passwordRequired) {
          this.view.set('admin-login');
          this.loginForm.reset({ password: '' });
        } else if (!registered) {
          this.cancelEdit();
          this.userForm.patchValue({ doi, doiType });
          this.view.set('registration');
        } else {
          this.analysisForm.reset({ description: '' });
          this.view.set('standard-analysis');
        }
        this.checkingDocument.set(false);
      },
      error: () => {
        this.accessMessage.set('No pudimos verificar el documento. Inténtalo nuevamente.');
        this.checkingDocument.set(false);
      }
    });
  }

  backHome(): void {
    this.view.set('home');
    this.accessMessage.set('');
    this.loginForm.reset({ password: '' });
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
    this.authenticating.set(true);
    this.accessMessage.set('');
    this.userService.login(doi, password).subscribe({
      next: ({ accessToken }) => {
        this.userService.setAccessToken(accessToken);
        this.authenticating.set(false);
        this.view.set('admin');
        this.loadUsers();
      },
      error: () => {
        this.accessMessage.set('Documento o contraseña incorrectos.');
        this.authenticating.set(false);
      }
    });
  }

  logout(): void {
    this.userService.clearAccessToken();
    this.users.set([]);
    this.analyses.set([]);
    this.cancelEdit();
    this.adminSection.set('users');
    this.profileMessage.set('');
    this.backHome();
  }

  setAdminSection(section: 'users' | 'predictions' | 'profile'): void {
    this.adminSection.set(section);
    if (section === 'predictions') {
      this.loadAnalyses();
    } else if (section === 'profile') {
      this.loadProfileForm();
    }
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
    const headers = ['ID', 'Nombre completo', 'Documento', 'Nacimiento', 'Género', 'Correo', 'Teléfono', 'Celular', 'Tipo', 'Profesión', 'Estado'];
    const rows = this.users().map(user => [
      String(user.userId ?? ''),
      `${user.firstName} ${user.paternalLastName} ${user.maternalLastName}`,
      `${user.doiType} ${user.doi}`,
      user.birthDate,
      user.gender,
      user.email,
      user.phone,
      user.mobilePhone,
      user.userType,
      user.profession,
      user.status ?? ''
    ]);
    const csv = '\uFEFF' + [headers, ...rows]
      .map(row => row.map(value => this.csvCell(value)).join(','))
      .join('\r\n');
    const link = this.document.createElement('a');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));

    link.href = url;
    link.download = `usuarios-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
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

  loadUsers(): void {
    this.loading.set(true);
    this.errorMessage.set('');

    this.userService.findAll().subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('No se pudieron cargar los usuarios.');
        this.loading.set(false);
      }
    });
  }

  registerStandard(): void {
    if (this.userForm.invalid) {
      this.userForm.markAllAsTouched();
      this.accessMessage.set('Completa correctamente todos los campos obligatorios.');
      return;
    }

    this.saving.set(true);
    this.accessMessage.set('');
    this.userService.create(this.registrationPayload()).subscribe({
      next: () => {
        this.saving.set(false);
        this.cancelEdit();
        this.analysisForm.reset({ description: '' });
        this.view.set('standard-analysis');
      },
      error: () => {
        this.saving.set(false);
        this.accessMessage.set('No se pudo completar el registro. Verifica los datos e inténtalo nuevamente.');
      }
    });
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
      birthDate: user.birthDate,
      gender: user.gender,
      email: user.email,
      phone: user.phone,
      mobilePhone: user.mobilePhone,
      profession: user.profession,
      password: ''
    });
    this.showEditModal.set(true);
  }

  cancelEdit(): void {
    this.editingUserId.set(null);
    this.editingUserType.set('STANDARD');
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
      password: ''
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

  promoteToAdmin(user: User): void {
    if (!user.userId || user.userType?.toUpperCase() !== 'STANDARD') {
      return;
    }

    const accepted = confirm(`¿Seguro que quieres convertir al usuario #${user.userId} en administrador?`);
    if (!accepted) {
      return;
    }

    this.errorMessage.set('');
    this.userService.update(user.userId, {
      ...user,
      userType: 'ADMIN',
      password: 'admin'
    }).subscribe({
      next: () => this.loadUsers(),
      error: () => this.errorMessage.set('No se pudo convertir el usuario en administrador.')
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
      birthDate: admin.birthDate,
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
      birthDate: formValue.birthDate,
      gender: formValue.gender,
      email: formValue.email,
      phone: formValue.phone,
      mobilePhone: formValue.mobilePhone,
      profession: formValue.profession
    };
  }
}
