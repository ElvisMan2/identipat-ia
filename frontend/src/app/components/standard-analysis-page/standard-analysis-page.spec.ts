import { FormControl, FormGroup, Validators } from '@angular/forms';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { StandardAnalysisPageComponent } from './standard-analysis-page';

describe('StandardAnalysisPageComponent', () => {
  it('rotates through distinct curiosities every six seconds while analysis is submitting', () => {
    jasmine.clock().install();
    TestBed.configureTestingModule({
      imports: [StandardAnalysisPageComponent],
      providers: [provideZonelessChangeDetection()]
    });
    const fixture = TestBed.createComponent(StandardAnalysisPageComponent);
    const component = fixture.componentInstance;
    component.analysisForm = new FormGroup({
      description: new FormControl('', [Validators.required])
    });
    fixture.componentRef.setInput('submittingAnalysis', true);
    fixture.detectChanges();

    const paragraph = () => fixture.nativeElement.querySelector('.analysis-fact p')?.textContent.trim();
    const firstCuriosity = paragraph();
    jasmine.clock().tick(6_000);
    fixture.detectChanges();

    expect(component.curiosities).toContain(firstCuriosity);
    expect(paragraph()).not.toBe(firstCuriosity);

    fixture.componentRef.setInput('submittingAnalysis', false);
    fixture.detectChanges();
    fixture.destroy();
    jasmine.clock().uninstall();
  });

  it('renders the analysis form and emits submit without owning its orchestration', () => {
    TestBed.configureTestingModule({
      imports: [StandardAnalysisPageComponent],
      providers: [provideZonelessChangeDetection()]
    });
    const fixture = TestBed.createComponent(StandardAnalysisPageComponent);
    const component = fixture.componentInstance;
    component.analysisForm = new FormGroup({
      description: new FormControl('', [Validators.required])
    });
    component.adminAuthenticated = true;
    fixture.detectChanges();

    const submit = jasmine.createSpy('submit');
    component.submitAnalysis.subscribe(submit);
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));

    expect(fixture.nativeElement.textContent).toContain('Cuéntanos qué quieres proteger');
    expect(fixture.nativeElement.querySelector('#description')).not.toBeNull();
    expect(submit).toHaveBeenCalledTimes(1);
  });

  it('shows and dismisses the oversized PDF modal', () => {
    TestBed.configureTestingModule({
      imports: [StandardAnalysisPageComponent],
      providers: [provideZonelessChangeDetection()]
    });
    const fixture = TestBed.createComponent(StandardAnalysisPageComponent);
    const component = fixture.componentInstance;
    component.analysisForm = new FormGroup({
      description: new FormControl('', [Validators.required])
    });
    component.pdfErrorMessage = 'El archivo supera el peso máximo permitido de 10 MiB.';
    fixture.detectChanges();

    const dismissed = jasmine.createSpy('dismissed');
    component.dismissPdfTooLarge.subscribe(dismissed);
    expect(fixture.nativeElement.querySelector('[role="dialog"]')?.textContent).toContain('El archivo supera el peso máximo permitido de 10 MiB.');
    (fixture.nativeElement.querySelector('.pdf-limit-modal button') as HTMLButtonElement).click();

    expect(dismissed).toHaveBeenCalledTimes(1);
  });

  it('renders specific PDF validation messages in the modal', () => {
    TestBed.configureTestingModule({
      imports: [StandardAnalysisPageComponent],
      providers: [provideZonelessChangeDetection()]
    });
    const fixture = TestBed.createComponent(StandardAnalysisPageComponent);
    const component = fixture.componentInstance;
    component.analysisForm = new FormGroup({
      description: new FormControl('', [Validators.required])
    });

    const messages = [
      'El PDF supera el máximo configurado de páginas (50 por defecto).',
      'El PDF debe contener al menos 20 caracteres de texto legible (mínimo predeterminado).',
      'El texto extraído supera los 20.000 caracteres (máximo predeterminado).',
      'El PDF está cifrado o protegido por contraseña.'
    ];

    for (const message of messages) {
      fixture.componentRef.setInput('pdfErrorMessage', message);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[role="dialog"]')?.textContent).toContain(message);
    }
  });
});