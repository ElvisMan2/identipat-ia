import { Component, EventEmitter, Input, Output } from '@angular/core';

export type AdminSection = 'users' | 'predictions' | 'profile' | 'settings';

@Component({
  selector: 'app-admin-panel-shell',
  standalone: true,
  templateUrl: './admin-panel-shell.html',
  styleUrl: './admin-panel-shell.css'
})
export class AdminPanelShellComponent {
  @Input() section: AdminSection = 'predictions';
  @Input() navigationPending: 'admin' | 'predictions' | 'exit' | null = null;
  @Output() logout = new EventEmitter<void>();
  @Output() openPredictions = new EventEmitter<void>();
  @Output() sectionChange = new EventEmitter<AdminSection>();
}
