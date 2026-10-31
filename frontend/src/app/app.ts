import { CommonModule } from '@angular/common';
import { DOCUMENT } from '@angular/common';
import { Component, computed, inject, Signal, signal, WritableSignal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { CKEditorModule } from '@ckeditor/ckeditor5-angular';
import { Alignment, Bold, ClassicEditor, Essentials, FindAndReplace, Heading, Image, ImageInsert, Italic, Link, List, Paragraph, RemoveFormat, SourceEditing, Strikethrough, Underline } from 'ckeditor5';
import 'ckeditor5/ckeditor5.css';
import { Observable } from 'rxjs';
import { StandardUserRegistrationRequest, UserService } from './services/user.service';
import { User } from './models/user.model';
import { AdminAnalysis, AnalysisStatus } from './services/admin-analysis.service';
import { AdminReportDisclaimerService, ReportDisclaimer } from './services/admin-report-disclaimer.service';
import { AdminProtectionGuideService, ProtectionGuide } from './services/admin-protection-guide.service';
import { AnalysisResponse, StandardAnalysisService } from './services/standard-analysis.service';

const adminSettingsReloadKey = 'identipat.admin.reloadSection';
type AdditionalOrientationType = 'INDUSTRIAL_DESIGN' | 'COPYRIGHT' | 'DISTINCTIVE_SIGN' | 'PLANT_BREEDER_CERTIFICATE' | 'TRADE_SECRET';

interface OrientationEditorState {
  shortDescription: WritableSignal<string>;
  savedShortDescription: WritableSignal<string>;
  shortDescriptionInvalid: Signal<boolean>;
  html: WritableSignal<string>;
  savedHtml: WritableSignal<string>;
  hasChanges: Signal<boolean>;
  canSave: Signal<boolean>;
  saving: WritableSignal<boolean>;
  message: WritableSignal<string>;
  error: WritableSignal<string>;
  latestGuide: WritableSignal<ProtectionGuide | null>;
  savedDraft: WritableSignal<ProtectionGuide | null>;
  activating: WritableSignal<boolean>;
  showPublishModal: WritableSignal<boolean>;
  editorInstance: { setData(data: string): void } | null;
}

@Component({
  selector: 'app-root',
  imports: [CommonModule, ReactiveFormsModule, CKEditorModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private readonly fb = inject(FormBuilder);
  private readonly document = inject(DOCUMENT);
  private readonly userService = inject(UserService);
  private readonly adminReportDisclaimerService = inject(AdminReportDisclaimerService);
  private readonly adminProtectionGuideService = inject(AdminProtectionGuideService);
  private readonly standardAnalysisService = inject(StandardAnalysisService);
  private readonly defaultAdminPassword = 'admin';

  readonly users = signal<User[]>([]);
  readonly userPage = signal(0);
  readonly userPageSize = signal(20);
  readonly userSearch = signal('');
  readonly userStatus = signal('');
  readonly userType = signal('');
  readonly userTotalPages = signal(0);
  readonly userTotalElements = signal(0);
  readonly downloadingUsers = signal(false);
  readonly analyses = signal<AdminAnalysis[]>([]);
  readonly latestReportDisclaimer = signal<ReportDisclaimer | null>(null);
  readonly disclaimerTitle = signal('');
  readonly savedDisclaimerTitle = signal('');
  readonly disclaimerContent = signal('');
  readonly savedDisclaimerContent = signal('');
  readonly hasUnsavedDisclaimerChanges = computed(() =>
    this.latestReportDisclaimer() !== null
    && (this.disclaimerTitle() !== this.savedDisclaimerTitle()
      || this.disclaimerContent() !== this.savedDisclaimerContent())
  );
  readonly settingsLoading = signal(false);
  readonly settingsError = signal('');
  readonly settingsLoaded = signal(false);
  readonly savingDisclaimerDraft = signal(false);
  readonly disclaimerDraftMessage = signal('');
  readonly disclaimerDraftError = signal('');
  readonly savedDisclaimerDraft = signal<ReportDisclaimer | null>(null);
  readonly activatingDisclaimer = signal(false);
  readonly disclaimerActivationMessage = signal('');
  readonly disclaimerActivationError = signal('');
  readonly showPublishDisclaimerModal = signal(false);
  readonly patentOrientationShortDescription = signal('');
  readonly savedPatentOrientationShortDescription = signal('');
  readonly patentOrientationShortDescriptionInvalid = computed(() => {
    const length = this.patentOrientationShortDescription().trim().length;
    return length > 0 && (length < 30 || length > 240);
  });
  readonly patentOrientationHtml = signal('');
  readonly savedPatentOrientationHtml = signal('');
  readonly hasPatentOrientationChanges = computed(() =>
    this.patentOrientationShortDescription() !== this.savedPatentOrientationShortDescription()
    || this.patentOrientationHtml() !== this.savedPatentOrientationHtml()
  );
  readonly patentOrientationCanSave = computed(() => {
    const descriptionLength = this.patentOrientationShortDescription().trim().length;
    return descriptionLength >= 30 && descriptionLength <= 240 && this.patentOrientationHtml().trim().length > 0;
  });
  readonly savingPatentOrientation = signal(false);
  readonly patentOrientationMessage = signal('');
  readonly patentOrientationError = signal('');
  readonly latestPatentGuide = signal<ProtectionGuide | null>(null);
  readonly savedPatentOrientationDraft = signal<ProtectionGuide | null>(null);
  readonly activatingPatentOrientation = signal(false);
  readonly showPublishPatentOrientationModal = signal(false);
  readonly utilityModelOrientationShortDescription = signal('');
  readonly savedUtilityModelOrientationShortDescription = signal('');
  readonly utilityModelOrientationShortDescriptionInvalid = computed(() => {
    const length = this.utilityModelOrientationShortDescription().trim().length;
    return length > 0 && (length < 30 || length > 240);
  });
  readonly utilityModelOrientationHtml = signal('');
  readonly savedUtilityModelOrientationHtml = signal('');
  readonly hasUtilityModelOrientationChanges = computed(() =>
    this.utilityModelOrientationShortDescription() !== this.savedUtilityModelOrientationShortDescription()
    || this.utilityModelOrientationHtml() !== this.savedUtilityModelOrientationHtml()
  );
  readonly utilityModelOrientationCanSave = computed(() => {
    const descriptionLength = this.utilityModelOrientationShortDescription().trim().length;
    return descriptionLength >= 30 && descriptionLength <= 240 && this.utilityModelOrientationHtml().trim().length > 0;
  });
  readonly savingUtilityModelOrientation = signal(false);
  readonly utilityModelOrientationMessage = signal('');
  readonly utilityModelOrientationError = signal('');
  readonly latestUtilityModelGuide = signal<ProtectionGuide | null>(null);
  readonly savedUtilityModelOrientationDraft = signal<ProtectionGuide | null>(null);
  readonly activatingUtilityModelOrientation = signal(false);
  readonly showPublishUtilityModelOrientationModal = signal(false);
  readonly additionalOrientationSections: ReadonlyArray<{ type: AdditionalOrientationType; label: string }> = [
    { type: 'INDUSTRIAL_DESIGN', label: 'Diseño industrial' },
    { type: 'COPYRIGHT', label: 'Derecho de autor' },
    { type: 'DISTINCTIVE_SIGN', label: 'Signo distintivo' },
    { type: 'PLANT_BREEDER_CERTIFICATE', label: 'Certificado de obtentor' },
    { type: 'TRADE_SECRET', label: 'Secreto empresarial' }
  ];
  private readonly additionalOrientationStates = new Map<AdditionalOrientationType, OrientationEditorState>(
    this.additionalOrientationSections.map(section => [section.type, this.createOrientationEditorState()])
  );
  readonly patentEditor = ClassicEditor;
  private patentEditorInstance: { setData(data: string): void } | null = null;
  readonly utilityModelEditor = ClassicEditor;
  private utilityModelEditorInstance: { setData(data: string): void } | null = null;
  readonly patentEditorConfig = {
    licenseKey: 'GPL',
    plugins: [Essentials, Paragraph, Heading, Bold, Italic, Underline, Strikethrough, RemoveFormat, Alignment, List, Link, Image, ImageInsert, FindAndReplace, SourceEditing],
    toolbar: ['undo', 'redo', '|', 'heading', '|', 'bold', 'italic', 'underline', 'strikethrough', '|', 'removeFormat', '|', 'alignment', '|', 'bulletedList', 'numberedList', 'link', 'insertImage', '|', 'findAndReplace', '|', 'sourceEditing'],
    link: {
      decorators: {
        openInNewTab: {
          mode: 'manual',
          label: 'Abrir en una pestaña nueva',
          attributes: {
            target: '_blank',
            rel: 'noopener noreferrer'
          }
        }
      }
    },
    image: {
      insert: {
        integrations: ['insertImageViaUrl']
      }
    }
  };
  readonly additionalOrientationEditor = ClassicEditor;
  readonly loading = signal(false);
  readonly analysesLoading = signal(false);
  readonly saving = signal(false);
  readonly registrationModal = signal<'registering' | 'success' | null>(null);
  readonly errorMessage = signal('');
  readonly editErrorMessage = signal('');
  readonly analysesError = signal('');
  readonly editingUserId = signal<number | null>(null);
  readonly editingUserType = signal('STANDARD');
  readonly editingOriginalUserType = signal('STANDARD');
  readonly showAdminPassword = signal(false);
  readonly showEditModal = signal(false);
  readonly adminSection = signal<'users' | 'predictions' | 'profile' | 'settings'>('users');
  readonly view = signal<'home' | 'registration' | 'standard-analysis' | 'standard-result' | 'admin-login' | 'admin' | 'legal'>('home');
  readonly checkingDocument = signal(false);
  readonly standardAccessPending = signal(false);
  readonly authenticating = signal(false);
  readonly adminAuthenticated = signal(false);
  readonly navigationPending = signal<'admin' | 'predictions' | 'exit' | null>(null);
  readonly exitError = signal('');
  readonly accessMessage = signal('');
  readonly savingProfile = signal(false);
  readonly profileMessage = signal('');
  readonly savingPassword = signal(false);
  readonly passwordMessage = signal('');
  readonly passwordSuccessModal = signal(false);
  readonly adminProfile = signal<User | null>(null);
  readonly submittingAnalysis = signal(false);
  readonly analysisMessage = signal('');
  readonly analysisResult = signal<AnalysisResponse | null>(null);
  readonly downloadingReportPdf = signal(false);
  readonly reportPdfError = signal('');
  readonly selectedPdf = signal<File | null>(null);
  readonly selectedAudio = signal<File | null>(null);
  readonly recordingAudio = signal(false);
  readonly recordingMilliseconds = signal(0);
  private mediaRecorder: MediaRecorder | null = null;
  private recordingStream: MediaStream | null = null;
  private recordingChunks: Blob[] = [];
  private recordingTimer: number | null = null;
  private recordingStartedAt = 0;

  constructor() {
    if (this.document.location.pathname === '/terminos-y-privacidad') {
      this.view.set('legal');
    }
    this.accessForm.controls.doiType.valueChanges.subscribe(() => this.accessForm.controls.doi.updateValueAndValidity());
    this.userForm.controls.doiType.valueChanges.subscribe(() => this.userForm.controls.doi.updateValueAndValidity());

    const adminIdentity = this.userService.getAdminIdentity();
    if (adminIdentity) {
      this.accessForm.setValue(adminIdentity);
      this.adminAuthenticated.set(true);
      this.adminSection.set(this.consumeAdminSettingsReloadTarget());
      this.view.set('admin');
      this.userService.findByDoi(adminIdentity.doi).subscribe({
        next: admin => {
          if (admin.userType?.toUpperCase() !== 'ADMIN' || admin.doiType !== adminIdentity.doiType) {
            this.logout();
            return;
          }

          this.adminProfile.set(admin);
          if (this.adminSection() === 'profile') {
            this.loadProfileForm();
          }
          this.loadUsers(0);
          if (this.adminSection() === 'settings') {
            this.loadReportDisclaimers();
            this.loadPatentOrientationGuide();
          }
        },
        error: () => this.logout()
      });
    }
  }

  readonly currentAdmin = computed<User | null>(() => {
    const authenticatedAdmin = this.adminProfile();
    if (authenticatedAdmin) {
      return authenticatedAdmin;
    }

    const { doi, doiType } = this.accessForm.getRawValue();
    return this.users().find(user =>
      user.userType?.toUpperCase() === 'ADMIN'
      && user.doi === doi
      && user.doiType === doiType
    ) ?? null;
  });

  readonly accessForm = this.fb.nonNullable.group({
    doiType: ['DNI', [Validators.required]],
    doi: ['', [Validators.required, this.documentValidator.bind(this)]]
  });

  readonly loginForm = this.fb.nonNullable.group({
    password: ['', [Validators.required]]
  });

  readonly userForm = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(25)]],
    paternalLastName: ['', [Validators.required, Validators.maxLength(25)]],
    maternalLastName: ['', [Validators.required, Validators.maxLength(25)]],
    doi: ['', [Validators.required, this.documentValidator.bind(this)]],
    doiType: ['', [Validators.required]],
    birthDate: ['', [Validators.required, this.dateValidator]],
    gender: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', [Validators.required, Validators.maxLength(9), Validators.pattern(/^\d+$/)]],
    mobilePhone: ['', [Validators.required, Validators.maxLength(15), this.mobilePhoneValidator]],
    profession: ['', [Validators.required]],
    password: [''],
    termsAccepted: [false, [Validators.requiredTrue]]
  });

  readonly profileForm = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(25)]],
    paternalLastName: ['', [Validators.required, Validators.maxLength(25)]],
    maternalLastName: ['', [Validators.required, Validators.maxLength(25)]],
    birthDate: ['', [Validators.required, this.dateValidator]],
    gender: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', [Validators.required, Validators.maxLength(9), Validators.pattern(/^\d+$/)]],
    mobilePhone: ['', [Validators.required, Validators.maxLength(15), this.mobilePhoneValidator]],
    profession: ['', [Validators.required]]
  });

  readonly securityForm = this.fb.nonNullable.group({
    password: [''],
    confirmPassword: ['']
  }, { validators: this.passwordMatchValidator });

  readonly analysisForm = this.fb.nonNullable.group({
    description: ['', [Validators.required, Validators.minLength(20), Validators.maxLength(20000)]]
  });

  private documentValidator(control: AbstractControl): ValidationErrors | null {
    if (!control.value) {
      return null;
    }

    const doiType = control.parent?.get('doiType')?.value;
    const pattern = doiType === 'DNI' ? /^\d{8}$/ : /^\d{6,11}$/;
    return pattern.test(control.value) ? null : { doiFormat: true };
  }

  private passwordMatchValidator(control: AbstractControl): ValidationErrors | null {
    const password = control.get('password')?.value ?? '';
    const confirmPassword = control.get('confirmPassword')?.value ?? '';
    return password === confirmPassword ? null : { passwordMismatch: true };
  }

  private dateValidator(control: AbstractControl): ValidationErrors | null {
    if (!control.value) {
      return null;
    }

    return /^(?:\d{4}-\d{2}-\d{2}|\d{2}\/\d{2}\/\d{4})$/.test(control.value)
      ? null
      : { dateFormat: true };
  }

  private mobilePhoneValidator(control: AbstractControl): ValidationErrors | null {
    if (!control.value) {
      return null;
    }

    const normalized = String(control.value).replace(/\s/g, '');
    const isValid = /^\d{9}$/.test(normalized)
      || /^\+51\d{9}$/.test(normalized);
    return isValid ? null : { mobilePhoneFormat: true };
  }

  sanitizeMobilePhone(event: Event, control: AbstractControl): void {
    const input = event.target as HTMLInputElement;
    const hasLeadingPlus = input.value.trimStart().startsWith('+');
    const sanitized = `${hasLeadingPlus ? '+' : ''}${input.value
      .replace(/\+/g, '')
      .replace(/[^\d ]/g, '')
      .trimStart()}`;

    if (input.value !== sanitized) {
      input.value = sanitized;
      control.setValue(sanitized, { emitEvent: false });
    }
  }

  sanitizePhone(event: Event, control: AbstractControl): void {
    const input = event.target as HTMLInputElement;
    const sanitized = input.value.replace(/\D/g, '').slice(0, 9);
    if (input.value !== sanitized) {
      input.value = sanitized;
      control.setValue(sanitized, { emitEvent: false });
    }
  }

  sanitizeDni(event: Event, control: AbstractControl): void {
    const input = event.target as HTMLInputElement;
    const sanitized = input.value.replace(/\D/g, '').slice(0, 8);
    if (input.value !== sanitized) {
      input.value = sanitized;
      control.setValue(sanitized, { emitEvent: false });
    }
  }

  private toDateInputValue(date: string): string {
    const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(date);
    return match ? `${match[3]}-${match[2]}-${match[1]}` : date.slice(0, 10);
  }

  private toBackendDate(date: string): string {
    const isoMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
    return isoMatch ? `${isoMatch[3]}/${isoMatch[2]}/${isoMatch[1]}` : date;
  }

  identifyDocument(): void {
    if (this.accessForm.invalid) {
      this.accessForm.markAllAsTouched();
      return;
    }

    const { doi, doiType } = this.accessForm.getRawValue();
    const accessStartedAt = Date.now();
    this.checkingDocument.set(true);
    this.standardAccessPending.set(false);
    this.accessMessage.set('');
    this.userService.identify(doi, doiType).subscribe({
      next: ({ registered, passwordRequired, active }) => {
        this.standardAccessPending.set(registered && !passwordRequired);
        this.finishAccess(accessStartedAt, () => {
          if (registered && active === false) {
            this.accessMessage.set('Usuario inactivo');
          } else if (passwordRequired) {
            this.view.set('admin-login');
            this.loginForm.reset({ password: '' });
          } else if (!registered) {
            this.accessMessage.set('Usuario no registrado');
          } else {
            this.analysisForm.reset({ description: '' });
            this.analysisMessage.set('');
            this.view.set('standard-analysis');
            this.scrollToTop();
          }
          this.standardAccessPending.set(false);
          this.checkingDocument.set(false);
        });
      },
      error: () => {
        this.finishAccess(accessStartedAt, () => {
          this.standardAccessPending.set(false);
          this.accessMessage.set('No pudimos verificar el documento. Inténtalo nuevamente.');
          this.checkingDocument.set(false);
        });
      }
    });
  }

  private finishAccess(startedAt: number, complete: () => void): void {
    const remainingMinimumTime = Math.max(0, 500 - (Date.now() - startedAt));
    setTimeout(complete, remainingMinimumTime);
  }

  private scrollToTop(): void {
    this.document.defaultView?.scrollTo({ top: 0, left: 0, behavior: 'auto' });
  }

  backHome(): void {
    this.view.set('home');
    this.accessMessage.set('');
    this.loginForm.reset({ password: '' });
  }

  exitSession(): void {
    this.accessForm.reset({ doiType: 'DNI', doi: '' });
    this.analysisForm.reset({ description: '' });
    this.analysisMessage.set('');
    this.analysisResult.set(null);
    this.backHome();
  }

  exitAnalysis(): void {
    this.exitWithSessionClose(() => this.backHome());
  }

  exitResult(): void {
    this.exitWithSessionClose(() => this.exitSession());
  }

  private exitWithSessionClose(navigate: () => void): void {
    if (this.adminAuthenticated()) {
      this.scheduleNavigation('exit', navigate);
      return;
    }

    if (this.navigationPending()) {
      return;
    }

    this.navigationPending.set('exit');
    this.exitError.set('');
    this.standardAnalysisService.closeStandardSession().subscribe({
      next: () => {
        this.navigationPending.set(null);
        navigate();
      },
      error: () => {
        this.navigationPending.set(null);
        this.exitError.set('No se pudo cerrar la sesión. Comprueba tu conexión e inténtalo nuevamente.');
      }
    });
  }

  openRegistration(): void {
    this.cancelEdit();
    this.accessMessage.set('');
    this.view.set('registration');
  }

  startNewAnalysis(): void {
    this.analysisForm.reset({ description: '' });
    this.analysisMessage.set('');
    this.analysisResult.set(null);
    this.view.set('standard-analysis');
    this.scrollToTop();
  }

  cancelRegistration(): void {
    this.cancelEdit();
    this.backHome();
  }

  loginAdmin(): void {
    if (this.accessForm.invalid || this.loginForm.invalid) {
      this.accessForm.markAllAsTouched();
      this.loginForm.markAllAsTouched();
      return;
    }

    const { doi } = this.accessForm.getRawValue();
    const { password } = this.loginForm.getRawValue();
    const accessStartedAt = Date.now();
    this.authenticating.set(true);
    this.accessMessage.set('');
    this.userService.login(doi, password).subscribe({
      next: ({ accessToken }) => {
        this.finishAccess(accessStartedAt, () => {
          const { doiType } = this.accessForm.getRawValue();
          this.userService.setAccessToken(accessToken, doi, doiType);
          this.authenticating.set(false);
          this.adminAuthenticated.set(true);
          this.analysisForm.reset({ description: '' });
          this.analysisMessage.set('');
          this.view.set('standard-analysis');
          this.scrollToTop();
        });
      },
      error: () => {
        this.finishAccess(accessStartedAt, () => {
          this.accessMessage.set('Documento o contraseña incorrectos.');
          this.authenticating.set(false);
        });
      }
    });
  }

  logout(): void {
    this.userService.clearAccessToken();
    this.adminAuthenticated.set(false);
    this.adminProfile.set(null);
    this.users.set([]);
    this.analyses.set([]);
    this.cancelEdit();
    this.adminSection.set('users');
    this.profileMessage.set('');
    this.backHome();
  }

  openAdminPanel(): void {
    if (this.navigationPending()) {
      return;
    }

    this.navigationPending.set('admin');
    window.setTimeout(() => {
      this.navigationPending.set(null);
      this.adminSection.set('users');
      this.view.set('admin');
      this.loadUsers(0);
      this.loadAdminProfile();
      this.scrollToTop();
    }, 500);
  }

  openPredictions(): void {
    this.scheduleNavigation('predictions', () => this.startNewAnalysis());
  }

  private scheduleNavigation(
    destination: 'admin' | 'predictions' | 'exit',
    navigate: () => void
  ): void {
    if (this.navigationPending()) {
      return;
    }

    this.navigationPending.set(destination);
    window.setTimeout(() => {
      this.navigationPending.set(null);
      navigate();
    }, 500);
  }

  setAdminSection(section: 'users' | 'predictions' | 'profile' | 'settings'): void {
    this.adminSection.set(section);
    if (section === 'predictions') {
      this.loadAnalyses();
    } else if (section === 'profile') {
      this.loadProfileForm();
    } else if (section === 'settings' && !this.settingsLoaded() && !this.settingsLoading()) {
      this.loadReportDisclaimers();
      this.loadPatentOrientationGuide();
    }
  }

  loadReportDisclaimers(): void {
    if (this.hasUnsavedDisclaimerChanges() || this.savingDisclaimerDraft() || this.activatingDisclaimer()) {
      return;
    }

    this.settingsLoading.set(true);
    this.settingsError.set('');
    this.adminReportDisclaimerService.getAll(this.userService.getAccessToken()).subscribe({
      next: disclaimers => {
        const latestDisclaimer = disclaimers.reduce<ReportDisclaimer | null>(
          (latest, disclaimer) => !latest || disclaimer.version > latest.version ? disclaimer : latest,
          null
        );
        this.latestReportDisclaimer.set(latestDisclaimer);
        this.disclaimerTitle.set(latestDisclaimer?.title ?? '');
        this.savedDisclaimerTitle.set(latestDisclaimer?.title ?? '');
        this.disclaimerContent.set(latestDisclaimer?.content ?? '');
        this.savedDisclaimerContent.set(latestDisclaimer?.content ?? '');
        this.savedDisclaimerDraft.set(latestDisclaimer?.status === 'DRAFT' ? latestDisclaimer : null);
        this.disclaimerDraftMessage.set('');
        this.disclaimerDraftError.set('');
        this.disclaimerActivationMessage.set('');
        this.disclaimerActivationError.set('');
        this.settingsLoaded.set(true);
        this.settingsLoading.set(false);
      },
      error: () => {
        this.settingsError.set('No se pudieron cargar los disclaimers. Inténtalo nuevamente.');
        this.settingsLoading.set(false);
      }
    });
  }

  updateDisclaimerTitle(event: Event): void {
    this.disclaimerTitle.set((event.target as HTMLInputElement).value);
    this.disclaimerDraftMessage.set('');
    this.disclaimerDraftError.set('');
    this.disclaimerActivationMessage.set('');
    this.disclaimerActivationError.set('');
  }

  updateDisclaimerContent(event: Event): void {
    this.disclaimerContent.set((event.target as HTMLTextAreaElement).value);
    this.disclaimerDraftMessage.set('');
    this.disclaimerDraftError.set('');
    this.disclaimerActivationMessage.set('');
    this.disclaimerActivationError.set('');
  }

  updatePatentOrientationHtml(event: { editor: { getData(): string } }): void {
    this.patentOrientationHtml.set(this.normalizePatentOrientationHtml(event.editor.getData()));
    this.patentOrientationMessage.set('');
    this.patentOrientationError.set('');
  }

  private normalizePatentOrientationHtml(html: string): string {
    if (!html.trim() || typeof DOMParser === 'undefined') {
      return html;
    }

    const parsedDocument = new DOMParser().parseFromString(`<body>${html}</body>`, 'text/html');
    const allowedElements = new Set(['p', 'h1', 'h2', 'h3', 'h4', 'div', 'strong', 'em', 'u', 's', 'ul', 'ol', 'li', 'a', 'img']);
    const removedContentElements = new Set(['script', 'style', 'iframe', 'object', 'embed', 'template', 'form']);

    Array.from(parsedDocument.body.querySelectorAll('*')).reverse().forEach(element => {
      const elementName = element.tagName.toLowerCase();
      if (elementName === 'br') {
        element.replaceWith(parsedDocument.createTextNode('\u00A0'));
      } else if (removedContentElements.has(elementName)) {
        element.remove();
      } else if (!allowedElements.has(elementName)) {
        while (element.firstChild) {
          element.parentNode?.insertBefore(element.firstChild, element);
        }
        element.remove();
      }
    });

    parsedDocument.body.querySelectorAll('*').forEach(element => {
      const elementName = element.tagName.toLowerCase();
      Array.from(element.attributes).forEach(attribute => {
        const attributeName = attribute.name.toLowerCase();
        if (attributeName === 'data-list-item-id') {
          element.removeAttribute(attribute.name);
          return;
        }

        const allowed = attributeName === 'style'
          || (elementName === 'a' && (attributeName === 'target' || attributeName === 'rel'))
          || (elementName === 'a' && attributeName === 'href')
          || (elementName === 'img' && (attributeName === 'src' || attributeName === 'alt'));
        if (!allowed) {
          element.removeAttribute(attribute.name);
        }
      });

      if (elementName === 'a') {
        const href = element.getAttribute('href');
        if (!href || !this.isAllowedPatentOrientationUrl(href)) {
          element.setAttribute('href', 'https://example.com/');
        }
        if (element.getAttribute('target') === '_blank') {
          element.setAttribute('rel', 'noopener noreferrer');
        } else {
          element.removeAttribute('target');
          element.removeAttribute('rel');
        }
      } else if (elementName === 'img') {
        const src = element.getAttribute('src');
        if (src && !this.isAllowedPatentOrientationUrl(src)) {
          element.removeAttribute('src');
        }
      }
    });
    return parsedDocument.body.innerHTML;
  }

  private isAllowedPatentOrientationUrl(value: string): boolean {
    try {
      const url = new URL(value);
      return url.protocol === 'https:' && url.hostname.length > 0;
    } catch {
      return false;
    }
  }

  updatePatentOrientationShortDescription(event: Event): void {
    this.patentOrientationShortDescription.set((event.target as HTMLInputElement).value);
    this.patentOrientationMessage.set('');
    this.patentOrientationError.set('');
  }

  onPatentEditorReady(editor: { setData(data: string): void }): void {
    this.patentEditorInstance = editor;
    const html = this.patentOrientationHtml();
    if (html) {
      editor.setData(html);
    }
  }

  savePatentOrientationDraft(): void {
    if (this.savingPatentOrientation()) {
      return;
    }

    this.savingPatentOrientation.set(true);
    this.patentOrientationMessage.set('');
    this.patentOrientationError.set('');
    const normalizedHtml = this.normalizePatentOrientationHtml(this.patentOrientationHtml());
    this.patentOrientationHtml.set(normalizedHtml);
    this.adminProtectionGuideService.createDraft(
      this.userService.getAccessToken(),
      'INVENTION_PATENT',
      this.patentOrientationShortDescription(),
      normalizedHtml
    ).subscribe({
      next: draft => {
        this.latestPatentGuide.set(draft);
        this.patentOrientationShortDescription.set(draft.shortDescription);
        this.savedPatentOrientationShortDescription.set(draft.shortDescription);
        const normalizedDraftHtml = this.normalizePatentOrientationHtml(draft.nextStepsContentHtml);
        this.patentOrientationHtml.set(normalizedDraftHtml);
        this.savedPatentOrientationHtml.set(normalizedDraftHtml);
        this.patentEditorInstance?.setData(normalizedDraftHtml);
        this.savedPatentOrientationDraft.set(draft);
        this.showPatentOrientationMessage('Borrador de orientación guardado correctamente.');
        this.savingPatentOrientation.set(false);
      },
      error: () => {
        this.patentOrientationError.set('No se pudo guardar el borrador de orientación. Inténtalo nuevamente.');
        this.savingPatentOrientation.set(false);
      }
    });
  }

  loadPatentOrientationGuide(): void {
    this.adminProtectionGuideService.getAll(this.userService.getAccessToken()).subscribe({
      next: guides => {
        const latestPatentGuide = guides
          .filter(guide => guide.protectionType === 'INVENTION_PATENT')
          .sort((left, right) => right.version - left.version)[0] ?? null;
        this.latestPatentGuide.set(latestPatentGuide);
        this.savedPatentOrientationDraft.set(latestPatentGuide?.status === 'DRAFT' ? latestPatentGuide : null);
        this.patentOrientationShortDescription.set(latestPatentGuide?.shortDescription ?? '');
        this.savedPatentOrientationShortDescription.set(latestPatentGuide?.shortDescription ?? '');
        const html = this.normalizePatentOrientationHtml(latestPatentGuide?.nextStepsContentHtml ?? '');
        this.patentOrientationHtml.set(html);
        this.savedPatentOrientationHtml.set(html);
        this.patentEditorInstance?.setData(html);

        const latestUtilityModelGuide = guides
          .filter(guide => guide.protectionType === 'UTILITY_MODEL')
          .sort((left, right) => right.version - left.version)[0] ?? null;
        this.latestUtilityModelGuide.set(latestUtilityModelGuide);
        this.savedUtilityModelOrientationDraft.set(latestUtilityModelGuide?.status === 'DRAFT' ? latestUtilityModelGuide : null);
        this.utilityModelOrientationShortDescription.set(latestUtilityModelGuide?.shortDescription ?? '');
        this.savedUtilityModelOrientationShortDescription.set(latestUtilityModelGuide?.shortDescription ?? '');
        const utilityModelHtml = this.normalizePatentOrientationHtml(latestUtilityModelGuide?.nextStepsContentHtml ?? '');
        this.utilityModelOrientationHtml.set(utilityModelHtml);
        this.savedUtilityModelOrientationHtml.set(utilityModelHtml);
        this.utilityModelEditorInstance?.setData(utilityModelHtml);

        this.additionalOrientationSections.forEach(section => {
          const state = this.orientationState(section.type);
          const latestGuide = guides
            .filter(guide => guide.protectionType === section.type)
            .sort((left, right) => right.version - left.version)[0] ?? null;
          state.latestGuide.set(latestGuide);
          state.savedDraft.set(latestGuide?.status === 'DRAFT' ? latestGuide : null);
          state.shortDescription.set(latestGuide?.shortDescription ?? '');
          state.savedShortDescription.set(latestGuide?.shortDescription ?? '');
          const html = this.normalizePatentOrientationHtml(latestGuide?.nextStepsContentHtml ?? '');
          state.html.set(html);
          state.savedHtml.set(html);
          state.editorInstance?.setData(html);
        });
      },
      error: () => {
        this.patentOrientationError.set('No se pudo consultar el estado de la orientación.');
      }
    });
  }

  updateUtilityModelOrientationHtml(event: { editor: { getData(): string } }): void {
    this.utilityModelOrientationHtml.set(this.normalizePatentOrientationHtml(event.editor.getData()));
    this.utilityModelOrientationMessage.set('');
    this.utilityModelOrientationError.set('');
  }

  updateUtilityModelOrientationShortDescription(event: Event): void {
    this.utilityModelOrientationShortDescription.set((event.target as HTMLInputElement).value);
    this.utilityModelOrientationMessage.set('');
    this.utilityModelOrientationError.set('');
  }

  onUtilityModelEditorReady(editor: { setData(data: string): void }): void {
    this.utilityModelEditorInstance = editor;
    const html = this.utilityModelOrientationHtml();
    if (html) {
      editor.setData(html);
    }
  }

  saveUtilityModelOrientationDraft(): void {
    if (this.savingUtilityModelOrientation()) {
      return;
    }

    this.savingUtilityModelOrientation.set(true);
    this.utilityModelOrientationMessage.set('');
    this.utilityModelOrientationError.set('');
    const normalizedHtml = this.normalizePatentOrientationHtml(this.utilityModelOrientationHtml());
    this.utilityModelOrientationHtml.set(normalizedHtml);
    this.adminProtectionGuideService.createDraft(
      this.userService.getAccessToken(),
      'UTILITY_MODEL',
      this.utilityModelOrientationShortDescription(),
      normalizedHtml
    ).subscribe({
      next: draft => {
        this.latestUtilityModelGuide.set(draft);
        this.utilityModelOrientationShortDescription.set(draft.shortDescription);
        this.savedUtilityModelOrientationShortDescription.set(draft.shortDescription);
        const normalizedDraftHtml = this.normalizePatentOrientationHtml(draft.nextStepsContentHtml);
        this.utilityModelOrientationHtml.set(normalizedDraftHtml);
        this.savedUtilityModelOrientationHtml.set(normalizedDraftHtml);
        this.utilityModelEditorInstance?.setData(normalizedDraftHtml);
        this.savedUtilityModelOrientationDraft.set(draft);
        this.utilityModelOrientationMessage.set('Borrador de orientación guardado correctamente.');
        this.savingUtilityModelOrientation.set(false);
      },
      error: () => {
        this.utilityModelOrientationError.set('No se pudo guardar el borrador de orientación. Inténtalo nuevamente.');
        this.savingUtilityModelOrientation.set(false);
      }
    });
  }

  activateUtilityModelOrientationDraft(): void {
    const draft = this.savedUtilityModelOrientationDraft();
    if (draft) {
      const sanitizedHtml = this.normalizePatentOrientationHtml(draft.nextStepsContentHtml);
      const sanitizedDraft = { ...draft, nextStepsContentHtml: sanitizedHtml };
      this.savedUtilityModelOrientationDraft.set(sanitizedDraft);
      this.utilityModelOrientationHtml.set(sanitizedHtml);
      this.savedUtilityModelOrientationHtml.set(sanitizedHtml);
    }

    const guide = this.savedUtilityModelOrientationDraft();
    if (!this.showPublishUtilityModelOrientationModal() || !guide
      || this.hasUtilityModelOrientationChanges() || this.activatingUtilityModelOrientation()) {
      return;
    }

    this.activatingUtilityModelOrientation.set(true);
    this.utilityModelOrientationMessage.set('');
    this.utilityModelOrientationError.set('');
    this.adminProtectionGuideService.activate(this.userService.getAccessToken(), guide.guideId).subscribe({
      next: activeGuide => {
        this.showPublishUtilityModelOrientationModal.set(false);
        this.latestUtilityModelGuide.set(activeGuide);
        this.savedUtilityModelOrientationDraft.set(null);
        this.utilityModelOrientationMessage.set('Orientación publicada correctamente.');
        this.activatingUtilityModelOrientation.set(false);
        this.scheduleAdminSettingsReload();
      },
      error: () => {
        this.utilityModelOrientationError.set('No se pudo publicar la orientación. Inténtalo nuevamente.');
        this.activatingUtilityModelOrientation.set(false);
      }
    });
  }

  openPublishUtilityModelOrientationModal(): void {
    if (!this.savedUtilityModelOrientationDraft() || this.hasUtilityModelOrientationChanges()
      || this.savingUtilityModelOrientation() || this.activatingUtilityModelOrientation()) {
      return;
    }

    this.utilityModelOrientationError.set('');
    this.showPublishUtilityModelOrientationModal.set(true);
  }

  closePublishUtilityModelOrientationModal(): void {
    if (!this.activatingUtilityModelOrientation()) {
      this.showPublishUtilityModelOrientationModal.set(false);
    }
  }

  activatePatentOrientationDraft(): void {
    const draft = this.savedPatentOrientationDraft();
    if (draft) {
      const sanitizedHtml = this.normalizePatentOrientationHtml(draft.nextStepsContentHtml);
      const sanitizedDraft = { ...draft, nextStepsContentHtml: sanitizedHtml };
      this.savedPatentOrientationDraft.set(sanitizedDraft);
      this.patentOrientationHtml.set(sanitizedHtml);
      this.savedPatentOrientationHtml.set(sanitizedHtml);
    }

    const guide = this.savedPatentOrientationDraft();
    if (!this.showPublishPatentOrientationModal() || !guide
      || this.hasPatentOrientationChanges() || this.activatingPatentOrientation()) {
      return;
    }

    this.activatingPatentOrientation.set(true);
    this.patentOrientationMessage.set('');
    this.patentOrientationError.set('');
    this.adminProtectionGuideService.activate(this.userService.getAccessToken(), guide.guideId).subscribe({
      next: activeGuide => {
        this.showPublishPatentOrientationModal.set(false);
        this.latestPatentGuide.set(activeGuide);
        this.savedPatentOrientationDraft.set(null);
        this.showPatentOrientationMessage('Orientación publicada correctamente.');
        this.activatingPatentOrientation.set(false);
        this.scheduleAdminSettingsReload();
      },
      error: () => {
        this.patentOrientationError.set('No se pudo publicar la orientación. Inténtalo nuevamente.');
        this.activatingPatentOrientation.set(false);
      }
    });
  }

  openPublishPatentOrientationModal(): void {
    if (!this.savedPatentOrientationDraft() || this.hasPatentOrientationChanges()
      || this.savingPatentOrientation() || this.activatingPatentOrientation()) {
      return;
    }

    this.patentOrientationError.set('');
    this.showPublishPatentOrientationModal.set(true);
  }

  closePublishPatentOrientationModal(): void {
    if (!this.activatingPatentOrientation()) {
      this.showPublishPatentOrientationModal.set(false);
    }
  }

  private showPatentOrientationMessage(message: string): void {
    this.patentOrientationMessage.set(message);
  }

  saveDisclaimerDraft(): void {
    const latestDisclaimer = this.latestReportDisclaimer();
    if (!latestDisclaimer || !this.hasUnsavedDisclaimerChanges() || this.savingDisclaimerDraft()) {
      return;
    }

    this.savingDisclaimerDraft.set(true);
    this.disclaimerDraftMessage.set('');
    this.disclaimerDraftError.set('');
    this.adminReportDisclaimerService.createDraft(
      this.userService.getAccessToken(),
      this.disclaimerTitle(),
      this.disclaimerContent()
    ).subscribe({
      next: draft => {
        this.latestReportDisclaimer.set(draft);
        this.disclaimerTitle.set(draft.title);
        this.savedDisclaimerTitle.set(draft.title);
        this.disclaimerContent.set(draft.content);
        this.savedDisclaimerContent.set(draft.content);
        this.savedDisclaimerDraft.set(draft);
        this.disclaimerDraftMessage.set('Borrador guardado correctamente.');
        this.savingDisclaimerDraft.set(false);
      },
      error: () => {
        this.disclaimerDraftError.set('No se pudo guardar el borrador. Revisa el contenido e inténtalo nuevamente.');
        this.savingDisclaimerDraft.set(false);
      }
    });
  }

  publishDisclaimerDraft(): void {
    const draft = this.savedDisclaimerDraft();
    if (!this.showPublishDisclaimerModal() || !draft || this.hasUnsavedDisclaimerChanges()
      || this.savingDisclaimerDraft() || this.activatingDisclaimer()) {
      return;
    }

    this.activatingDisclaimer.set(true);
    this.disclaimerActivationMessage.set('');
    this.disclaimerActivationError.set('');
    this.adminReportDisclaimerService.activate(this.userService.getAccessToken(), draft.id).subscribe({
      next: activeDisclaimer => {
        this.showPublishDisclaimerModal.set(false);
        this.latestReportDisclaimer.set(activeDisclaimer);
        this.disclaimerTitle.set(activeDisclaimer.title);
        this.savedDisclaimerTitle.set(activeDisclaimer.title);
        this.disclaimerContent.set(activeDisclaimer.content);
        this.savedDisclaimerContent.set(activeDisclaimer.content);
        this.savedDisclaimerDraft.set(null);
        this.disclaimerDraftMessage.set('');
        this.disclaimerActivationMessage.set('Disclaimer publicado correctamente.');
        this.activatingDisclaimer.set(false);
        this.scheduleAdminSettingsReload();
      },
      error: () => {
        this.disclaimerActivationError.set('No se pudo publicar el borrador. Inténtalo nuevamente.');
        this.activatingDisclaimer.set(false);
      }
    });
  }

  openPublishDisclaimerModal(): void {
    if (!this.savedDisclaimerDraft() || this.hasUnsavedDisclaimerChanges()
      || this.savingDisclaimerDraft() || this.activatingDisclaimer()) {
      return;
    }

    this.disclaimerActivationError.set('');
    this.showPublishDisclaimerModal.set(true);
  }

  closePublishDisclaimerModal(): void {
    if (!this.activatingDisclaimer()) {
      this.showPublishDisclaimerModal.set(false);
    }
  }

  private consumeAdminSettingsReloadTarget(): 'users' | 'settings' {
    try {
      const storage = this.document.defaultView?.sessionStorage;
      const section = storage?.getItem(adminSettingsReloadKey);
      storage?.removeItem(adminSettingsReloadKey);
      return section === 'settings' ? 'settings' : 'users';
    } catch {
      return 'users';
    }
  }

  private scheduleAdminSettingsReload(): void {
    const browserWindow = this.document.defaultView;
    if (!browserWindow) {
      return;
    }

    browserWindow.setTimeout(() => {
      if (!this.adminAuthenticated()) {
        return;
      }

      try {
        browserWindow.sessionStorage.setItem(adminSettingsReloadKey, 'settings');
      } catch {
        return;
      }

      browserWindow.location.reload();
    }, 1000);
  }

  private loadAdminProfile(): void {
    const { doi, doiType } = this.accessForm.getRawValue();
    this.userService.findByDoi(doi).subscribe({
      next: admin => {
        if (admin.userType?.toUpperCase() !== 'ADMIN' || admin.doiType !== doiType) {
          return;
        }

        this.adminProfile.set(admin);
        if (this.adminSection() === 'profile') {
          this.loadProfileForm();
        }
      }
    });
  }

  loadAnalyses(): void {
    this.analysesError.set('');
  }

  downloadAnalysesCsv(): void {
    const headers = ['Fecha', 'Usuario', 'Estado', 'Predicción'];
    const rows = this.analyses().map(analysis => [
      analysis.requestedAt,
      `${analysis.firstName} ${analysis.paternalLastName} ${analysis.maternalLastName} (Usuario #${analysis.userId})`,
      this.analysisStatusLabel(analysis.status),
      analysis.summary || 'Resultado aún no disponible.'
    ]);
    const csv = '\uFEFF' + [headers, ...rows]
      .map(row => row.map(value => this.csvCell(value)).join(','))
      .join('\r\n');
    const link = this.document.createElement('a');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));

    link.href = url;
    link.download = `predicciones-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  private createOrientationEditorState(): OrientationEditorState {
    const shortDescription = signal('');
    const savedShortDescription = signal('');
    const html = signal('');
    const savedHtml = signal('');
    return {
      shortDescription,
      savedShortDescription,
      shortDescriptionInvalid: computed(() => {
        const length = shortDescription().trim().length;
        return length > 0 && (length < 30 || length > 240);
      }),
      html,
      savedHtml,
      hasChanges: computed(() => shortDescription() !== savedShortDescription() || html() !== savedHtml()),
      canSave: computed(() => {
        const descriptionLength = shortDescription().trim().length;
        return descriptionLength >= 30 && descriptionLength <= 240 && html().trim().length > 0;
      }),
      saving: signal(false),
      message: signal(''),
      error: signal(''),
      latestGuide: signal<ProtectionGuide | null>(null),
      savedDraft: signal<ProtectionGuide | null>(null),
      activating: signal(false),
      showPublishModal: signal(false),
      editorInstance: null
    };
  }

  orientationState(type: AdditionalOrientationType): OrientationEditorState {
    return this.additionalOrientationStates.get(type)!;
  }

  updateAdditionalOrientationHtml(type: AdditionalOrientationType, event: { editor: { getData(): string } }): void {
    const state = this.orientationState(type);
    state.html.set(this.normalizePatentOrientationHtml(event.editor.getData()));
    state.message.set('');
    state.error.set('');
  }

  updateAdditionalOrientationShortDescription(type: AdditionalOrientationType, event: Event): void {
    const state = this.orientationState(type);
    state.shortDescription.set((event.target as HTMLInputElement).value);
    state.message.set('');
    state.error.set('');
  }

  onAdditionalOrientationEditorReady(type: AdditionalOrientationType, editor: { setData(data: string): void }): void {
    const state = this.orientationState(type);
    state.editorInstance = editor;
    const html = state.html();
    if (html) {
      editor.setData(html);
    }
  }

  saveAdditionalOrientationDraft(type: AdditionalOrientationType): void {
    const state = this.orientationState(type);
    if (state.saving()) {
      return;
    }

    state.saving.set(true);
    state.message.set('');
    state.error.set('');
    const normalizedHtml = this.normalizePatentOrientationHtml(state.html());
    state.html.set(normalizedHtml);
    this.adminProtectionGuideService.createDraft(
      this.userService.getAccessToken(),
      type,
      state.shortDescription(),
      normalizedHtml
    ).subscribe({
      next: draft => {
        state.latestGuide.set(draft);
        state.shortDescription.set(draft.shortDescription);
        state.savedShortDescription.set(draft.shortDescription);
        const normalizedDraftHtml = this.normalizePatentOrientationHtml(draft.nextStepsContentHtml);
        state.html.set(normalizedDraftHtml);
        state.savedHtml.set(normalizedDraftHtml);
        state.editorInstance?.setData(normalizedDraftHtml);
        state.savedDraft.set(draft);
        state.message.set('Borrador de orientación guardado correctamente.');
        state.saving.set(false);
      },
      error: () => {
        state.error.set('No se pudo guardar el borrador de orientación. Inténtalo nuevamente.');
        state.saving.set(false);
      }
    });
  }

  activateAdditionalOrientationDraft(type: AdditionalOrientationType): void {
    const state = this.orientationState(type);
    const draft = state.savedDraft();
    if (draft) {
      const sanitizedHtml = this.normalizePatentOrientationHtml(draft.nextStepsContentHtml);
      const sanitizedDraft = { ...draft, nextStepsContentHtml: sanitizedHtml };
      state.savedDraft.set(sanitizedDraft);
      state.html.set(sanitizedHtml);
      state.savedHtml.set(sanitizedHtml);
    }

    const guide = state.savedDraft();
    if (!state.showPublishModal() || !guide || state.hasChanges() || state.activating()) {
      return;
    }

    state.activating.set(true);
    state.message.set('');
    state.error.set('');
    this.adminProtectionGuideService.activate(this.userService.getAccessToken(), guide.guideId).subscribe({
      next: activeGuide => {
        state.showPublishModal.set(false);
        state.latestGuide.set(activeGuide);
        state.savedDraft.set(null);
        state.message.set('Orientación publicada correctamente.');
        state.activating.set(false);
        this.scheduleAdminSettingsReload();
      },
      error: () => {
        state.error.set('No se pudo publicar la orientación. Inténtalo nuevamente.');
        state.activating.set(false);
      }
    });
  }

  openPublishAdditionalOrientationModal(type: AdditionalOrientationType): void {
    const state = this.orientationState(type);
    if (!state.savedDraft() || state.hasChanges() || state.saving() || state.activating()) {
      return;
    }

    state.error.set('');
    state.showPublishModal.set(true);
  }

  closePublishAdditionalOrientationModal(type: AdditionalOrientationType): void {
    const state = this.orientationState(type);
    if (!state.activating()) {
      state.showPublishModal.set(false);
    }
  }

  downloadUsersCsv(): void {
    this.downloadingUsers.set(true);
    this.errorMessage.set('');
    this.userService.exportAll(this.userSearch(), this.userStatus(), this.userType()).subscribe({
      next: response => {
        const blob = response.body;
        if (!blob) {
          this.errorMessage.set('No se pudo descargar el archivo de usuarios.');
          this.downloadingUsers.set(false);
          return;
        }

        const contentDisposition = response.headers.get('Content-Disposition');
        const filename = contentDisposition?.match(/filename="?([^";]+)"?/i)?.[1]
          ?? `usuarios-${new Date().toISOString().slice(0, 10)}.csv`;
        const url = URL.createObjectURL(blob);
        const link = this.document.createElement('a');
        link.href = url;
        link.download = filename;
        link.click();
        window.setTimeout(() => URL.revokeObjectURL(url));
        this.downloadingUsers.set(false);
      },
      error: () => {
        this.errorMessage.set('No se pudieron exportar los usuarios.');
        this.downloadingUsers.set(false);
      }
    });
  }

  private csvCell(value: string): string {
    const safeValue = /^[=+\-@]/.test(value) ? `'${value}` : value;
    return `"${safeValue.replace(/"/g, '""')}"`;
  }

  analysisStatusLabel(status: AnalysisStatus): string {
    const labels: Record<AnalysisStatus, string> = {
      RECEIVED: 'Recibida',
      PREPROCESSING: 'Procesando',
      ANALYZING: 'Analizando',
      COMPLETED: 'Completada',
      FAILED: 'Fallida'
    };
    return labels[status];
  }

  loadUsers(page = this.userPage()): void {
    this.loading.set(true);
    this.errorMessage.set('');

    this.userService.findAll(page, this.userPageSize(), this.userSearch(), this.userStatus(), this.userType()).subscribe({
      next: (response) => {
        this.users.set(response.content);
        this.userPage.set(response.page);
        this.userTotalPages.set(response.totalPages);
        this.userTotalElements.set(response.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('No se pudieron cargar los usuarios.');
        this.loading.set(false);
      }
    });
  }

  changeUserPage(page: number): void {
    if (this.loading() || page < 0 || page >= this.userTotalPages()) {
      return;
    }

    this.loadUsers(page);
  }

  changeUserPageSize(event: Event): void {
    const size = Number((event.target as HTMLSelectElement).value);
    if (![10, 20, 50, 100].includes(size)) {
      return;
    }

    this.userPageSize.set(size);
    this.loadUsers(0);
  }

  updateUserSearch(event: Event): void {
    this.userSearch.set((event.target as HTMLInputElement).value);
  }

  searchUsers(): void {
    this.loadUsers(0);
  }

  changeEditingUserType(event: Event): void {
    const userType = (event.target as HTMLSelectElement).value;
    if (!['STANDARD', 'ADMIN'].includes(userType)) {
      return;
    }

    const wasAdmin = this.editingUserType() === 'ADMIN';
    this.editingUserType.set(userType);
    const passwordControl = this.userForm.controls.password;
    if (userType === 'ADMIN') {
      const passwordRequired = this.editingOriginalUserType() !== 'ADMIN';
      if (!wasAdmin && passwordRequired) {
        passwordControl.setValue(this.defaultAdminPassword);
      }
      if (passwordRequired) {
        passwordControl.setValidators([Validators.required]);
      } else {
        passwordControl.clearValidators();
      }
    } else {
      passwordControl.setValue('');
      passwordControl.clearValidators();
    }
    passwordControl.updateValueAndValidity({ emitEvent: false });
    if (this.editingOriginalUserType() === 'ADMIN' && userType === 'ADMIN') {
      this.userForm.controls.doi.disable({ emitEvent: false });
      this.userForm.controls.doiType.disable({ emitEvent: false });
    } else {
      this.userForm.controls.doi.enable({ emitEvent: false });
      this.userForm.controls.doiType.enable({ emitEvent: false });
    }
  }

  toggleAdminPasswordVisibility(): void {
    this.showAdminPassword.update(visible => !visible);
  }

  changeUserStatus(event: Event): void {
    const status = (event.target as HTMLSelectElement).value;
    if (!['', 'A', 'I'].includes(status)) {
      return;
    }

    this.userStatus.set(status);
    this.loadUsers(0);
  }

  changeUserType(event: Event): void {
    const userType = (event.target as HTMLSelectElement).value;
    if (!['', 'STANDARD', 'ADMIN'].includes(userType)) {
      return;
    }

    this.userType.set(userType);
    this.loadUsers(0);
  }

  registerStandard(): void {
    if (this.userForm.invalid) {
      this.userForm.markAllAsTouched();
      this.accessMessage.set('Completa correctamente todos los campos obligatorios.');
      return;
    }

    this.saving.set(true);
    const registrationStartedAt = Date.now();
    this.accessMessage.set('');
    this.userService.create(this.registrationPayload()).subscribe({
      next: () => {
        this.saving.set(false);
        const remainingMinimumTime = Math.max(0, 500 - (Date.now() - registrationStartedAt));
        setTimeout(() => {
          this.registrationModal.set('success');
          setTimeout(() => {
            this.registrationModal.set(null);
            this.cancelEdit();
            this.analysisForm.reset({ description: '' });
            this.analysisMessage.set('');
            this.view.set('standard-analysis');
            this.scrollToTop();
          }, 2000);
        }, remainingMinimumTime);
      },
      error: () => {
        this.saving.set(false);
        this.registrationModal.set(null);
        this.accessMessage.set('No se pudo completar el registro. Verifica los datos e inténtalo nuevamente.');
      }
    });
  }

  submitAnalysis(): void {
    const selectedPdf = this.selectedPdf();
    const selectedAudio = this.selectedAudio();
    if (!selectedPdf && !selectedAudio && this.analysisForm.invalid) {
      this.analysisForm.markAllAsTouched();
      return;
    }

    const { doi, doiType } = this.accessForm.getRawValue();
    const { description } = this.analysisForm.getRawValue();
    this.submittingAnalysis.set(true);
    this.analysisMessage.set('');
    this.analysisResult.set(null);

    const adminAccessToken = this.adminAuthenticated() ? this.userService.getAccessToken() : '';
    const request = selectedPdf
      ? this.standardAnalysisService.createPdfAnalysis(doi, doiType, selectedPdf, adminAccessToken)
      : selectedAudio
        ? this.standardAnalysisService.createAudioAnalysis(doi, doiType, selectedAudio, adminAccessToken)
        : this.standardAnalysisService.createTextAnalysis(doi, doiType, description, adminAccessToken);
    request.subscribe({
      next: ({ analysisId }) => {
        this.analysisMessage.set('');
        this.pollAnalysis(analysisId, adminAccessToken);
      },
      error: () => {
        this.submittingAnalysis.set(false);
        this.analysisMessage.set('No se pudo iniciar el análisis. Verifica tu sesión, consentimiento e intenta nuevamente.');
      }
    });
  }

  onPdfSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';

    if (!file) {
      return;
    }

    if (file.type !== 'application/pdf') {
      this.analysisMessage.set('Selecciona un archivo PDF válido.');
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      this.analysisMessage.set('El PDF no puede superar los 10 MiB.');
      return;
    }

    this.selectedPdf.set(file);
    this.selectedAudio.set(null);
    this.analysisForm.controls.description.setValue('');
    this.analysisForm.controls.description.markAsUntouched();
    this.analysisMessage.set('');
  }

  removeSelectedPdf(): void {
    this.selectedPdf.set(null);
    this.analysisForm.controls.description.setValue('');
    this.analysisForm.controls.description.markAsUntouched();
    this.analysisMessage.set('');
  }

  async toggleAudioRecording(): Promise<void> {
    if (this.recordingAudio()) {
      this.mediaRecorder?.stop();
      return;
    }

    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      this.analysisMessage.set('Tu navegador no permite grabar audio.');
      return;
    }

    try {
      this.recordingStream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4']
        .find(type => MediaRecorder.isTypeSupported(type)) ?? '';
      this.mediaRecorder = new MediaRecorder(this.recordingStream, mimeType ? { mimeType } : undefined);
      this.recordingChunks = [];
      this.mediaRecorder.ondataavailable = event => {
        if (event.data.size > 0) this.recordingChunks.push(event.data);
      };
      this.mediaRecorder.onstop = () => {
        const type = this.mediaRecorder?.mimeType || 'audio/webm';
        const extension = type.includes('mp4') ? 'm4a' : 'webm';
        const blob = new Blob(this.recordingChunks, { type });
        this.selectedAudio.set(new File([blob], `grabacion-${Date.now()}.${extension}`, { type }));
        this.selectedPdf.set(null);
        this.analysisForm.controls.description.setValue('');
        this.analysisForm.controls.description.markAsUntouched();
        this.recordingAudio.set(false);
        this.stopRecordingTimer();
        this.recordingStream?.getTracks().forEach(track => track.stop());
        this.recordingStream = null;
        this.analysisMessage.set('');
      };
      this.mediaRecorder.start();
      this.recordingStartedAt = Date.now();
      this.recordingMilliseconds.set(0);
      this.recordingTimer = window.setInterval(() => {
        this.recordingMilliseconds.set(Date.now() - this.recordingStartedAt);
      }, 50);
      this.recordingAudio.set(true);
      this.analysisMessage.set('Grabando... pulsa el botón para detener.');
    } catch {
      this.recordingAudio.set(false);
      this.stopRecordingTimer();
      this.recordingStream?.getTracks().forEach(track => track.stop());
      this.recordingStream = null;
      this.analysisMessage.set('No se pudo acceder al micrófono. Revisa los permisos del navegador.');
    }
  }

  stopAudioRecording(): void {
    if (this.recordingAudio()) {
      this.mediaRecorder?.stop();
    }
  }

  recordingTimeLabel(): string {
    const milliseconds = this.recordingMilliseconds();
    const minutes = Math.floor(milliseconds / 60000).toString().padStart(2, '0');
    const seconds = Math.floor((milliseconds % 60000) / 1000).toString().padStart(2, '0');
    const remainder = Math.floor((milliseconds % 1000) / 10).toString().padStart(2, '0');
    return `${minutes}:${seconds}:${remainder}`;
  }

  private stopRecordingTimer(): void {
    if (this.recordingTimer !== null) {
      window.clearInterval(this.recordingTimer);
      this.recordingTimer = null;
    }
  }

  removeSelectedAudio(): void {
    this.selectedAudio.set(null);
    this.analysisForm.controls.description.setValue('');
    this.analysisForm.controls.description.markAsUntouched();
    this.analysisMessage.set('');
  }

  private downloadAnalysisJson(analysisId: string, analysis: unknown): void {
    const link = this.document.createElement('a');
    const url = URL.createObjectURL(new Blob([
      JSON.stringify(analysis, null, 2)
    ], { type: 'application/json;charset=utf-8' }));

    link.href = url;
    link.download = `analysis-${analysisId}.json`;
    link.click();
    URL.revokeObjectURL(url);
  }

  downloadReportPdf(): void {
    const analysisId = this.analysisResult()?.analysisId;
    if (!analysisId || this.downloadingReportPdf()) {
      return;
    }

    const accessToken = this.adminAuthenticated() ? this.userService.getAccessToken() : '';
    this.downloadingReportPdf.set(true);
    this.reportPdfError.set('');
    this.standardAnalysisService.getReportPdf(analysisId, accessToken).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const link = this.document.createElement('a');
        link.href = url;
        link.download = `identipat-diagnostico-${analysisId}.pdf`;
        link.click();
        window.setTimeout(() => URL.revokeObjectURL(url));
        this.downloadingReportPdf.set(false);
      },
      error: () => {
        this.downloadingReportPdf.set(false);
        this.reportPdfError.set('No se pudo descargar el reporte PDF. Inténtalo nuevamente.');
      }
    });
  }

  private pollAnalysis(analysisId: string, accessToken = ''): void {
    this.standardAnalysisService.getAnalysis(analysisId, accessToken).subscribe({
      next: (analysis) => {
        this.analysisResult.set(analysis);
        console.log('JSON del análisis:', analysis);
        if (analysis.status === 'COMPLETED' || analysis.status === 'FAILED') {
          this.selectedPdf.set(null);
          this.selectedAudio.set(null);
          this.submittingAnalysis.set(false);
          this.analysisMessage.set(analysis.status === 'COMPLETED'
            ? ''
            : 'El análisis no pudo completarse.');
          this.downloadAnalysisJson(analysisId, analysis);
          this.view.set('standard-result');
          this.scrollToTop();
          return;
        }

        window.setTimeout(() => this.pollAnalysis(analysisId, accessToken), 2000);
      },
      error: (error) => {
        this.submittingAnalysis.set(false);
        this.analysisMessage.set('No se pudo consultar el resultado del análisis.');
        console.error('No se pudo consultar el análisis:', error);
      }
    });
  }

  protectionTypeLabel(type: string): string {
    const labels: Record<string, string> = {
      INVENTION_PATENT: 'Patente de invención',
      UTILITY_MODEL: 'Modelo de utilidad',
      INDUSTRIAL_DESIGN: 'Diseño industrial',
      DISTINCTIVE_SIGN: 'Signo distintivo',
      COPYRIGHT: 'Derechos de autor',
      OTHER: 'Otra modalidad'
    };
    return labels[type] ?? type;
  }

  applicabilityLabel(applicability: string): string {
    const labels: Record<string, string> = {
      POSSIBLE: 'Posible',
      LIKELY: 'Probable',
      UNLIKELY: 'Poco probable',
      NO_POTENTIAL_MATCH: 'Sin coincidencia potencial',
      INSUFFICIENT_INFORMATION: 'Información insuficiente'
    };
    return labels[applicability] ?? applicability;
  }

  submit(): void {
    const userId = this.editingUserId();
    if (userId === null) {
      return;
    }

    if (this.userForm.invalid) {
      this.userForm.markAllAsTouched();
      this.errorMessage.set('Completa correctamente todos los campos obligatorios.');
      return;
    }

    const formValue = this.userForm.getRawValue();
    const registrationPayload = this.registrationPayload();
    const userData = { ...registrationPayload, userType: this.editingUserType() };

    this.saving.set(true);
    this.errorMessage.set('');

    const request$: Observable<unknown> = this.editingOriginalUserType() === 'ADMIN'
      && this.editingUserType() === 'ADMIN'
      ? this.userService.updateProfile(userId, userData)
      : this.userService.update(userId, {
        ...userData,
        password: formValue.password || undefined
      });

    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.cancelEdit();
        this.loadUsers();
      },
      error: () => {
        this.errorMessage.set('No se pudo guardar el usuario.');
        this.saving.set(false);
      }
    });
  }

  deleteEditingUser(): void {
    const userId = this.editingUserId();
    if (userId === null) {
      return;
    }

    const accepted = confirm(
      `¿Eliminar al usuario #${userId}? Si tiene sesiones STANDARD, se conservará como inactivo.`
    );
    if (!accepted) {
      return;
    }

    this.saving.set(true);
    this.editErrorMessage.set('');
    this.userService.delete(userId).subscribe({
      next: () => {
        this.saving.set(false);
        this.cancelEdit();
        this.loadUsers();
      },
      error: () => {
        this.saving.set(false);
        this.editErrorMessage.set('No se pudo eliminar o inactivar el usuario.');
      }
    });
  }

  startEdit(user: User): void {
    if (!user.userId) {
      return;
    }

    this.editingUserId.set(user.userId);
    this.editErrorMessage.set('');
    this.editingUserType.set(user.userType);
    this.editingOriginalUserType.set(user.userType);
    this.showAdminPassword.set(false);
    const passwordControl = this.userForm.controls.password;
    passwordControl.clearValidators();
    passwordControl.updateValueAndValidity({ emitEvent: false });
    if (user.userType === 'ADMIN') {
      this.userForm.controls.doi.disable({ emitEvent: false });
      this.userForm.controls.doiType.disable({ emitEvent: false });
    } else {
      this.userForm.controls.doi.enable({ emitEvent: false });
      this.userForm.controls.doiType.enable({ emitEvent: false });
    }
    this.userForm.setValue({
      firstName: user.firstName,
      paternalLastName: user.paternalLastName,
      maternalLastName: user.maternalLastName,
      doi: user.doi,
      doiType: user.doiType,
      birthDate: this.toDateInputValue(user.birthDate),
      gender: user.gender,
      email: user.email,
      phone: user.phone,
      mobilePhone: user.mobilePhone,
      profession: user.profession,
      password: '',
      termsAccepted: true
    });
    this.showEditModal.set(true);
  }

  cancelEdit(): void {
    this.editingUserId.set(null);
    this.editErrorMessage.set('');
    this.editingUserType.set('STANDARD');
    this.editingOriginalUserType.set('STANDARD');
    this.showAdminPassword.set(false);
    this.showEditModal.set(false);
    this.userForm.controls.doi.enable({ emitEvent: false });
    this.userForm.controls.doiType.enable({ emitEvent: false });
    this.userForm.controls.password.clearValidators();
    this.userForm.controls.password.updateValueAndValidity({ emitEvent: false });
    this.userForm.reset({
      firstName: '',
      paternalLastName: '',
      maternalLastName: '',
      doi: '',
      doiType: '',
      birthDate: '',
      gender: '',
      email: '',
      phone: '',
      mobilePhone: '',
      profession: '',
      password: '',
      termsAccepted: false
    });
  }

  toggleStatus(user: User): void {
    if (!user.userId) {
      return;
    }

    const activating = user.status?.toUpperCase() === 'I';
    const action = activating ? 'activar' : 'inactivar';
    const accepted = confirm(`¿Seguro que quieres ${action} al usuario #${user.userId}?`);
    if (!accepted) {
      return;
    }

    this.errorMessage.set('');
    this.userService.update(user.userId, { ...user, status: activating ? 'A' : 'I' }).subscribe({
      next: () => this.loadUsers(),
      error: () => this.errorMessage.set(`No se pudo ${action} el usuario.`)
    });
  }

  isStandard(user: User): boolean {
    return user.userType?.toUpperCase() === 'STANDARD';
  }

  isInactive(user: User): boolean {
    return user.status?.toUpperCase() === 'I';
  }

  isEditing(user: User): boolean {
    return this.editingUserId() === user.userId;
  }

  private loadProfileForm(): void {
    this.profileMessage.set('');
    this.passwordMessage.set('');
    this.showAdminPassword.set(false);
    this.securityForm.reset({ password: '', confirmPassword: '' });
    const admin = this.currentAdmin();
    if (!admin) {
      return;
    }

    this.profileForm.setValue({
      firstName: admin.firstName,
      paternalLastName: admin.paternalLastName,
      maternalLastName: admin.maternalLastName,
      birthDate: this.toDateInputValue(admin.birthDate),
      gender: admin.gender,
      email: admin.email,
      phone: admin.phone,
      mobilePhone: admin.mobilePhone,
      profession: admin.profession
    });
  }

  updateProfile(): void {
    const admin = this.currentAdmin();
    if (!admin?.userId) {
      this.profileMessage.set('No se pudo identificar al administrador autenticado.');
      return;
    }

    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      this.profileMessage.set('Completa correctamente todos los campos obligatorios.');
      return;
    }

    const formValue = this.profileForm.getRawValue();
    const profileData: User = {
      ...admin,
      firstName: formValue.firstName,
      paternalLastName: formValue.paternalLastName,
      maternalLastName: formValue.maternalLastName,
      birthDate: this.toBackendDate(this.profileForm.controls.birthDate.value),
      gender: formValue.gender,
      email: formValue.email,
      phone: formValue.phone,
      mobilePhone: formValue.mobilePhone,
      profession: formValue.profession,
      doi: admin.doi,
      doiType: admin.doiType,
      userType: admin.userType,
      status: admin.status
    };
    this.savingProfile.set(true);
    this.profileMessage.set('');
    this.userService.updateProfile(admin.userId, profileData).subscribe({
      next: () => {
        this.savingProfile.set(false);
        this.profileMessage.set('Tus datos se actualizaron correctamente.');
        this.loadUsers();
      },
      error: () => {
        this.savingProfile.set(false);
        this.profileMessage.set('No se pudo actualizar tu información.');
      }
    });
  }

  updateAdminPassword(): void {
    const admin = this.currentAdmin();
    if (!admin?.userId) {
      this.passwordMessage.set('No se pudo identificar al administrador autenticado.');
      return;
    }

    if (this.securityForm.invalid || !this.securityForm.controls.password.value.trim()) {
      this.securityForm.markAllAsTouched();
      this.passwordMessage.set(this.securityForm.hasError('passwordMismatch')
        ? 'Las contraseñas no coinciden.'
        : 'Ingresa y confirma la nueva contraseña.');
      return;
    }

    this.savingPassword.set(true);
    this.passwordMessage.set('');
    this.userService.update(admin.userId, {
      ...admin,
      password: this.securityForm.controls.password.value
    }).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.passwordSuccessModal.set(true);
        this.securityForm.reset({ password: '', confirmPassword: '' });
        this.showAdminPassword.set(false);
      },
      error: () => {
        this.savingPassword.set(false);
        this.passwordMessage.set('No se pudo actualizar la contraseña.');
      }
    });
  }

  closePasswordSuccessModal(): void {
    this.passwordSuccessModal.set(false);
    this.document.defaultView?.location.reload();
  }

  private registrationPayload(): StandardUserRegistrationRequest {
    const formValue = this.userForm.getRawValue();
    return {
      firstName: formValue.firstName,
      paternalLastName: formValue.paternalLastName,
      maternalLastName: formValue.maternalLastName,
      doi: formValue.doi,
      doiType: formValue.doiType,
      birthDate: this.toBackendDate(formValue.birthDate),
      gender: formValue.gender,
      email: formValue.email,
      phone: formValue.phone,
      mobilePhone: formValue.mobilePhone,
      profession: formValue.profession
    };
  }
}
