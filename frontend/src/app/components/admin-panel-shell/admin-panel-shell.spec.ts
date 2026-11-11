import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AdminPanelShellComponent } from './admin-panel-shell';

describe('AdminPanelShellComponent', () => {
  it('renders the selected section and emits section changes', () => {
    TestBed.configureTestingModule({
      imports: [AdminPanelShellComponent],
      providers: [provideZonelessChangeDetection()]
    });
    const fixture = TestBed.createComponent(AdminPanelShellComponent);
    const component = fixture.componentInstance;
    component.section = 'users';
    fixture.detectChanges();

    const sectionChange = jasmine.createSpy('sectionChange');
    component.sectionChange.subscribe(sectionChange);
    const predictionsButton = Array.from(
      fixture.nativeElement.querySelectorAll('nav button') as NodeListOf<HTMLButtonElement>
    ).find(button => button.textContent?.trim() === 'Predicciones');
    predictionsButton?.click();

    expect(fixture.nativeElement.textContent).toContain('Gestión de usuarios');
    expect(sectionChange).toHaveBeenCalledWith('predictions');
  });
});
