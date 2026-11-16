import { FormControl, FormGroup, Validators } from '@angular/forms';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { StandardAnalysisPageComponent } from './standard-analysis-page';

describe('StandardAnalysisPageComponent', () => {
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
    component.pdfTooLarge = true;
    fixture.detectChanges();

    const dismissed = jasmine.createSpy('dismissed');
    component.dismissPdfTooLarge.subscribe(dismissed);
    expect(fixture.nativeElement.querySelector('[role="dialog"]')?.textContent).toContain('10 MiB');
    (fixture.nativeElement.querySelector('.pdf-limit-modal button') as HTMLButtonElement).click();

    expect(dismissed).toHaveBeenCalledTimes(1);
  });
});