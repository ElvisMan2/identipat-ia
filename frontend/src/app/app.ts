import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { StandardUserRegistrationRequest, UserService } from './services/user.service';
import { User } from './models/user.model';

@Component({
  selector: 'app-root',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(UserService);

  readonly users = signal<User[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly errorMessage = signal('');
  readonly editingUserId = signal<number | null>(null);
  readonly editingUserType = signal('STANDARD');
  readonly view = signal<'home' | 'admin-login' | 'admin'>('home');
  readonly checkingDocument = signal(false);
  readonly authenticating = signal(false);
  readonly accessMessage = signal('');

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
        } else {
          this.accessMessage.set(registered
            ? 'Documento reconocido. Ya puedes continuar con tu evaluación.'
            : 'No encontramos este documento. Completa tu registro para continuar.');
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
    this.cancelEdit();
    this.backHome();
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

  submit(): void {
    if (this.userForm.invalid) {
      this.userForm.markAllAsTouched();
      this.errorMessage.set('Completa correctamente todos los campos obligatorios.');
      return;
    }

    const formValue = this.userForm.getRawValue();
    const registrationPayload: StandardUserRegistrationRequest = {
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

    this.saving.set(true);
    this.errorMessage.set('');

    const userId = this.editingUserId();
    const request$: Observable<unknown> = userId === null
      ? this.userService.create(registrationPayload)
      : this.userService.update(userId, {
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
  }

  cancelEdit(): void {
    this.editingUserId.set(null);
    this.editingUserType.set('STANDARD');
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

  delete(user: User): void {
    if (!user.userId) {
      return;
    }

    const accepted = confirm(`¿Seguro que quieres eliminar al usuario #${user.userId}?`);
    if (!accepted) {
      return;
    }

    this.errorMessage.set('');
    this.userService.delete(user.userId).subscribe({
      next: () => this.loadUsers(),
      error: () => this.errorMessage.set('No se pudo eliminar el usuario.')
    });
  }

  isEditing(user: User): boolean {
    return this.editingUserId() === user.userId;
  }
}
