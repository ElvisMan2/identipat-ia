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
});