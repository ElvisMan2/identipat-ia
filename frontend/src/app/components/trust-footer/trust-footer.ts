import { DOCUMENT } from '@angular/common';
import { Component, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

@Component({
  selector: 'app-trust-footer',
  standalone: true,
  templateUrl: './trust-footer.html',
  styleUrl: './trust-footer.css'
})
export class TrustFooter {
  private readonly document = inject(DOCUMENT);
  private readonly platformId = inject(PLATFORM_ID);

  constructor() {
    if (isPlatformBrowser(this.platformId) && !this.document.getElementById('identipat-myriad-font')) {
      const fontUrl = new URL('MYRIADPRO-REGULAR.OTF', this.document.baseURI).href;
      const style = this.document.createElement('style');
      style.id = 'identipat-myriad-font';
      style.textContent = `@font-face { font-family: 'Myriad Pro'; src: url('${fontUrl}') format('opentype'); font-style: normal; font-weight: 400; font-display: swap; }`;
      this.document.head.appendChild(style);
    }
  }
}