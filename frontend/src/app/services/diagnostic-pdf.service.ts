import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';
import type {
  Content, ContentText, StyleDictionary, TDocumentDefinitions
} from 'pdfmake/interfaces';

const pointsPerMm = 72 / 25.4;

export function createDiagnosticPdfDefinition(root: HTMLElement): TDocumentDefinitions {
  if (!root.matches('.analysis-result') || !root.querySelector('.hero h2')) {
    throw new Error('The rendered diagnostic report is required');
  }

  const view = root.ownerDocument.defaultView;
  if (!view) {
    throw new Error('PDF export requires a browser document');
  }
  const computed = view.getComputedStyle(root);
  const color = (name: string, fallback: string) => computed.getPropertyValue(name).trim() || fallback;
  const ink = color('--ink', '#18332f');
  const muted = color('--muted', '#5c6d69');
  const accent = color('--teal', '#9d64f9');
  const line = color('--line', '#cfdbd7');

  const excluded = (element: Element) => element.matches(
    '[data-pdf-exclude], [hidden], [aria-hidden="true"], button, [role="dialog"]'
  ) || view.getComputedStyle(element).display === 'none';

  const inline = (node: Node, bold = false): ContentText[] => {
    if (node.nodeType === Node.TEXT_NODE) {
      return [{ text: (node.textContent ?? '').replace(/\s+/g, ' '), ...(bold ? { bold: true } : {}) }];
    }
    if (!(node instanceof Element) || excluded(node)) {
      return [];
    }
    if (node.tagName === 'BR') {
      return [{ text: '\n' }];
    }
    return Array.from(node.childNodes).flatMap(child =>
      inline(child, bold || node.matches('b, strong'))
    );
  };

  const text = (element: Element): ContentText[] => {
    const runs = inline(element);
    if (runs.length) {
      runs[0].text = String(runs[0].text).trimStart();
      const last = runs[runs.length - 1];
      last.text = String(last.text).trimEnd();
    }
    return runs;
  };

  const card = (content: Content[], fill: string, border = line): Content => ({
    table: {
      widths: ['*'],
      body: [[{ stack: content, fillColor: fill }]],
      dontBreakRows: false
    },
    layout: {
      hLineWidth: () => 0.6,
      vLineWidth: index => index === 0 ? 2 : 0.6,
      hLineColor: () => border,
      vLineColor: () => border,
      paddingLeft: () => 10,
      paddingRight: () => 10,
      paddingTop: () => 9,
      paddingBottom: () => 9
    },
    margin: [0, 4, 0, 8]
  });

  const convert = (element: Element): Content[] => {
    if (excluded(element)) {
      return [];
    }
    if (element.matches('.modality-heading')) {
      const parts = Array.from(element.children);
      if (parts.length !== 3) {
        throw new Error('Unsupported diagnostic modality heading');
      }
      return [{
        columns: [
          { text: text(parts[0]), style: 'modalityName', width: '*' },
          { text: text(parts[1]), style: 'description', width: '*' },
          {
            text: text(parts[2]), style: 'applicability', width: 85,
            background: color('--mint', '#dceee8')
          }
        ],
        columnGap: 10,
        margin: [0, 0, 0, 8],
        headlineLevel: 1
      }];
    }
    if (element.matches('ul, ol')) {
      const items = Array.from(element.children).filter(child => !excluded(child))
        .map(child => ({ text: text(child) }));
      return element.tagName === 'UL'
        ? [{ ul: items, margin: [0, 2, 0, 6] }]
        : [{ ol: items, margin: [0, 2, 0, 6] }];
    }
    if (element.matches('h1, h2, h3')) {
      return [{
        text: text(element),
        style: element.closest('.hero') ? 'title' : element.tagName === 'H3' ? 'subheading' : 'heading',
        headlineLevel: 1
      }];
    }
    if (element.matches('p, strong, b, .summary-kicker')) {
      const style = element.matches('.analysis-date') ? 'date'
        : element.matches('.summary-kicker') ? 'kicker'
        : element.matches('strong, b') ? 'question' : 'paragraph';
      return [{ text: text(element), style }];
    }
    if (!element.matches('section, article, div')) {
      throw new Error(`Unsupported diagnostic block: ${element.tagName}`);
    }

    const children = Array.from(element.children).flatMap(convert);
    if (!children.length) {
      return [];
    }
    if (element.matches('.summary-card')) {
      return [card(children, '#f0f8f5', accent)];
    }
    if (element.matches('.disclaimer-card, .context-card.warning')) {
      return [card(children, '#fffcf2', '#be933a')];
    }
    if (element.matches('.diag-item')) {
      return [card(children, '#f4f7f6', '#e3eae7')];
    }
    if (element.matches('.screening-article')) {
      return [card(children, '#ffffff', color('--coral', '#f2a123'))];
    }
    if (element.matches('.modalities-grid > article, .context-card')) {
      return [card(children, '#ffffff')];
    }
    return children;
  };

  const styles: StyleDictionary = {
    title: { font: 'SpaceGrotesk', fontSize: 24, bold: true, color: ink, margin: [0, 0, 0, 10] },
    heading: { font: 'SpaceGrotesk', fontSize: 15, bold: true, color: ink, margin: [0, 10, 0, 7] },
    subheading: { font: 'SpaceGrotesk', fontSize: 12, bold: true, color: ink, margin: [0, 0, 0, 5] },
    modalityName: { font: 'SpaceGrotesk', fontSize: 11, bold: true, color: ink },
    description: { fontSize: 9, color: muted },
    applicability: { fontSize: 9, bold: true, color: accent },
    date: { fontSize: 9, color: muted, margin: [0, 0, 0, 10] },
    kicker: { fontSize: 9, bold: true, color: accent, margin: [0, 0, 0, 4] },
    question: { fontSize: 10, bold: true, color: ink, margin: [0, 0, 0, 4] },
    paragraph: { margin: [0, 0, 0, 5] }
  };

  // Reading the DOM is synchronous: later navigation cannot change the exported snapshot.
  const content = Array.from(root.children).flatMap(convert);
  if (!content.length) {
    throw new Error('The diagnostic report is empty');
  }
  return {
    pageSize: 'A4',
    pageOrientation: 'portrait',
    pageMargins: 12 * pointsPerMm,
    defaultStyle: { font: 'IBMPlexSans', fontSize: 10, color: muted, lineHeight: 1.25 },
    styles,
    content,
    pageBreakBefore: (node, queries) => node.headlineLevel === 1
      && queries.getFollowingNodesOnPage().length === 0
      && queries.getPreviousNodesOnPage().length > 0
  };
}

@Injectable({ providedIn: 'root' })
export class DiagnosticPdfService {
  private readonly document = inject(DOCUMENT);

  async download(root: HTMLElement, analysisId: string): Promise<void> {
    const definition = createDiagnosticPdfDefinition(root);
    const pdfMake = (await import('pdfmake/build/pdfmake')).default;
    const fontUrl = (file: string) => new URL(`fonts/${file}`, this.document.baseURI).href;
    pdfMake.addFonts({
      IBMPlexSans: {
        normal: fontUrl('IBMPlexSans-Regular.ttf'),
        bold: fontUrl('IBMPlexSans-Bold.ttf')
      },
      SpaceGrotesk: {
        normal: fontUrl('SpaceGrotesk-Regular.ttf'),
        bold: fontUrl('SpaceGrotesk-Bold.ttf')
      }
    });
    const blob = await pdfMake.createPdf(definition).getBlob();
    const url = URL.createObjectURL(blob);
    try {
      const link = this.document.createElement('a');
      link.href = url;
      link.download = `identipat-diagnostico-${analysisId}.pdf`;
      this.document.body.appendChild(link);
      try {
        link.click();
      } finally {
        link.remove();
      }
    } finally {
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
  }
}
