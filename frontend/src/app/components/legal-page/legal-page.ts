import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TrustFooter } from '../trust-footer/trust-footer';
import { LegalDocument } from '../../services/legal-document.service';

@Component({
  selector: 'app-legal-page',
  standalone: true,
  imports: [TrustFooter],
  templateUrl: './legal-page.html',
  styleUrl: './legal-page.css'
})
export class LegalPageComponent {
  @Input() document: LegalDocument | null = null;
  @Input() loading = false;
  @Input() error = '';
  @Input() updatedAtLabel = '';
  @Output() retry = new EventEmitter<void>();
}