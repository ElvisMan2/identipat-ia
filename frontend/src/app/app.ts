import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserService } from './services/user.service';
import { User } from './models/user.model';

@Component({
  selector: 'app-root',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(UserService);

  readonly users = signal<User[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly errorMessage = signal('');
  readonly editingUserId = signal<number | null>(null);

  readonly userForm = this.fb.nonNullable.group({
    firstName: ['', [Validators.required]],
    paternalLastName: ['', [Validators.required]],
    maternalLastName: ['', [Validators.required]],
    currencyOfIncome: ['', [Validators.required]],
    monthlyIncome: [0, [Validators.required, Validators.min(0.01)]]
  });

  ngOnInit(): void {
    this.loadUsers();
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
      return;
    }

    const formValue = this.userForm.getRawValue();
    const payload: User = {
      firstName: formValue.firstName,
      paternalLastName: formValue.paternalLastName,
      maternalLastName: formValue.maternalLastName,
      currencyOfIncome: formValue.currencyOfIncome,
      monthlyIncome: Number(formValue.monthlyIncome)
    };

    this.saving.set(true);
    this.errorMessage.set('');

    const userId = this.editingUserId();
    const request$ = userId === null
      ? this.userService.create(payload)
      : this.userService.update(userId, payload);

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
    this.userForm.setValue({
      firstName: user.firstName,
      paternalLastName: user.paternalLastName,
      maternalLastName: user.maternalLastName,
      currencyOfIncome: user.currencyOfIncome,
      monthlyIncome: user.monthlyIncome
    });
  }

  cancelEdit(): void {
    this.editingUserId.set(null);
    this.userForm.reset({
      firstName: '',
      paternalLastName: '',
      maternalLastName: '',
      currencyOfIncome: '',
      monthlyIncome: 0
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
