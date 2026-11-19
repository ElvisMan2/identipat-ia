import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { createDiagnosticPdfDefinition, DiagnosticPdfService } from './diagnostic-pdf.service';

describe('diagnostic PDF', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
  });

  function report(optional = ''): HTMLElement {
    const root = document.createElement('section');
    root.className = 'analysis-result';
    root.innerHTML = `
      <section class="hero">
        <div class="hero-main"><div class="hero-left"><h2>Diagnóstico de creación</h2></div>
          <div data-pdf-exclude><button>Descargar PDF</button></div>
        </div>
        <p class="analysis-date">Fecha de análisis: 19 nov 2026</p>
      </section>
      <p data-pdf-exclude role="alert">Error temporal</p>
      <section class="summary-card"><div class="summary-content">
        <div class="summary-kicker">Resumen</div><p>Una invención útil, ¿qué proteger?</p>
      </div></section>
      <section class="modalities-grid"><h2>Modalidades identificadas</h2><article>
        <div class="modality-heading"><span>Patente</span><span>Descripción institucional</span><b>Posible</b></div>
        <div class="diagnosis-panel"><div class="diag-item">
          <strong>¿Por qué se identificó?</strong><p>Protección de una creación.</p>
        </div></div>
        <button data-pdf-exclude>Próximos pasos</button>
      </article></section>
      ${optional}
      <div data-pdf-exclude role="dialog"><p>Contenido privado del modal</p></div>
      <section class="disclaimer-card"><h2>Aviso orientativo</h2><p>No es una decisión definitiva.</p></section>`;
    document.body.appendChild(root);
    return root;
  }

  afterEach(() => {
    document.querySelectorAll('body > .analysis-result').forEach(element => element.remove());
  });

  it('exports visible sections and native styled text, excluding interface elements', () => {
    const root = report(`
      <section class="context-card"><h3>Recursos genéticos</h3>
        <p>Referencia <b>importante</b><br>Segunda línea.</p>
        <ul><li>Información faltante</li><li>Finalidad exacta</li></ul>
      </section>
      <section hidden><p>Oculto</p></section>
      <section style="display:none"><p>Invisible</p></section>`);
    const definition = createDiagnosticPdfDefinition(root);
    const serialized = JSON.stringify(definition.content);

    for (const included of [
      'Diagnóstico de creación', 'Fecha de análisis', 'Resumen', '¿qué proteger?',
      'Descripción institucional', 'Posible', 'Recursos genéticos', 'Información faltante',
      'Finalidad exacta', 'Aviso orientativo', 'No es una decisión definitiva.'
    ]) {
      expect(serialized).toContain(included);
    }
    for (const excluded of [
      'Descargar PDF', 'Error temporal', 'Próximos pasos', 'Contenido privado del modal', 'Oculto', 'Invisible'
    ]) {
      expect(serialized).not.toContain(excluded);
    }
    expect(serialized).toContain('"text":"importante","bold":true');
    expect(serialized).toContain('"text":"\\n"');
    expect(serialized).toContain('"ul":');
    expect(serialized).not.toContain('"image":');
    expect(definition.pageSize).toBe('A4');
    expect(definition.pageOrientation).toBe('portrait');
    expect(definition.pageMargins).toBeCloseTo(12 * 72 / 25.4, 6);
    expect(definition.header).toBeUndefined();
    expect(definition.footer).toBeUndefined();
  });

  it('keeps long paragraphs intact, allows cards to span pages, and snapshots content', () => {
    const longText = 'Texto extenso de diagnóstico con ñ y tildes. '.repeat(400);
    const root = report();
    root.querySelector('.diag-item p')!.textContent = longText;
    const definition = createDiagnosticPdfDefinition(root);
    root.replaceChildren();

    const serialized = JSON.stringify(definition.content);
    expect(serialized).toContain(longText.trim());
    expect(serialized).toContain('"dontBreakRows":false');
    expect(serialized).toContain('No es una decisión definitiva.');
    expect(serialized).not.toContain('"unbreakable":true');
  });

  it('rejects invalid roots and unsupported blocks instead of silently omitting content', () => {
    expect(() => createDiagnosticPdfDefinition(document.createElement('main'))).toThrowError();
    const root = report('<table><tr><td>Nuevo contenido</td></tr></table>');
    expect(() => createDiagnosticPdfDefinition(root)).toThrowError('Unsupported diagnostic block: TABLE');
  });

  it('propagates conversion failures without initiating a download', async () => {
    const service = TestBed.inject(DiagnosticPdfService);
    const createUrl = spyOn(URL, 'createObjectURL');
    await expectAsync(service.download(document.createElement('div'), 'analysis')).toBeRejected();
    expect(createUrl).not.toHaveBeenCalled();
  });
});
