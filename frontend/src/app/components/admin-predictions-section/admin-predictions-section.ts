import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AdminAnalysis } from '../../services/admin-analysis.service';

export type AdminPredictionsAction =
  | { type: 'download' }
  | { type: 'refresh' }
  | { type: 'search' }
  | { type: 'searchChange'; event: Event }
  | { type: 'dateFromChange'; event: Event }
  | { type: 'dateToChange'; event: Event }
  | { type: 'pageSizeChange'; event: Event }
  | { type: 'pageChange'; page: number };

@Component({
  selector: 'app-admin-predictions-section',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-predictions-section.html',
  styleUrl: './admin-predictions-section.css'
})
export class AdminPredictionsSectionComponent {
  @Input() analyses: AdminAnalysis[] = [];
  @Input() searchTerm = '';
  @Input() from = '';
  @Input() to = '';
  @Input() pageSize = 10;
  @Input() page = 0;
  @Input() totalPages = 0;
  @Input() totalElements = 0;
  @Input() loading = false;
  @Input() downloading = false;
  @Input() error = '';
  @Input() summarize = (value: string | null): string => value ?? '';
  @Output() action = new EventEmitter<AdminPredictionsAction>();
}
