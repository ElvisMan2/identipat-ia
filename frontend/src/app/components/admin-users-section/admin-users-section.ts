import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { User } from '../../models/user.model';

export type AdminUsersAction =
  | { type: 'search' }
  | { type: 'searchChange'; event: Event }
  | { type: 'statusChange'; event: Event }
  | { type: 'typeChange'; event: Event }
  | { type: 'pageSizeChange'; event: Event }
  | { type: 'download' }
  | { type: 'refresh' }
  | { type: 'pageChange'; page: number }
  | { type: 'edit'; user: User };

@Component({
  selector: 'app-admin-users-section',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-users-section.html',
  styleUrl: './admin-users-section.css'
})
export class AdminUsersSectionComponent {
  @Input() users: User[] = [];
  @Input() searchTerm = '';
  @Input() status = '';
  @Input() userType = '';
  @Input() pageSize = 10;
  @Input() page = 0;
  @Input() totalPages = 0;
  @Input() totalElements = 0;
  @Input() loading = false;
  @Input() downloading = false;
  @Input() error = '';
  @Input() editingUserId: number | null = null;
  @Output() action = new EventEmitter<AdminUsersAction>();
}
