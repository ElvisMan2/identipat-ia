import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { User } from '../../models/user.model';

@Component({
  selector: 'app-admin-profile-section',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './admin-profile-section.html',
  styleUrl: './admin-profile-section.css'
})
export class AdminProfileSectionComponent {
  @Input() currentAdmin: User | null = null;
  @Input() inactive = false;
  @Input() profileForm!: FormGroup;
  @Input() securityForm!: FormGroup;
  @Input() profileMessage = '';
  @Input() passwordMessage = '';
  @Input() savingProfile = false;
  @Input() savingPassword = false;
  @Input() showPassword = false;
  @Input() passwordSuccessModal = false;
  @Output() updateProfile = new EventEmitter<void>();
  @Output() updatePassword = new EventEmitter<void>();
  @Output() mobilePhoneInput = new EventEmitter<Event>();
  @Output() togglePasswordVisibility = new EventEmitter<void>();
  @Output() closeSuccessModal = new EventEmitter<void>();
}
