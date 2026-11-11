import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { LegalDocument } from '../../services/legal-document.service';
import { TrustFooter } from '../trust-footer/trust-footer';

export interface AccessSanitizeRequest {
  event: Event;
  control: AbstractControl;
}

@Component({
  selector: 'app-public-access-page',
  standalone: true,
  imports: [ReactiveFormsModule, TrustFooter],
  templateUrl: './public-access-page.html',
  styleUrl: './public-access-page.css'
})
export class PublicAccessPageComponent {
  @Input({ required: true }) view: 'home' | 'registration' | 'admin-login' | 'standard-analysis' | 'standard-result' | 'admin' | 'legal' = 'home';
  @Input({ required: true }) accessForm!: FormGroup;
  @Input({ required: true }) loginForm!: FormGroup;
  @Input({ required: true }) userForm!: FormGroup;
  @Input() legalLoading = false;
  @Input() legalError = '';
  @Input() legalDocument: LegalDocument | null = null;
  @Input() accessMessage = '';
  @Input() registrationModal: 'registering' | 'success' | null = null;
  @Input() saving = false;
  @Input() checkingDocument = false;
  @Input() standardAccessPending = false;
  @Input() authenticating = false;
  @Input() legalDateLabel = (_value: string | null | undefined): string => '';

  get passwordControl(): FormControl {
    return this.loginForm.controls['password'] as FormControl;
  }

  @Output() identify = new EventEmitter<void>();
  @Output() register = new EventEmitter<void>();
  @Output() login = new EventEmitter<void>();
  @Output() registrationOpen = new EventEmitter<void>();
  @Output() registrationCancel = new EventEmitter<void>();
  @Output() retryLegal = new EventEmitter<void>();
  @Output() sanitizeDni = new EventEmitter<AccessSanitizeRequest>();
  @Output() sanitizePhone = new EventEmitter<AccessSanitizeRequest>();
  @Output() sanitizeMobilePhone = new EventEmitter<AccessSanitizeRequest>();
}