import { Component, EventEmitter, Input, Output, Signal } from '@angular/core';
import { CKEditorModule } from '@ckeditor/ckeditor5-angular';
import { ClassicEditor, EditorConfig } from 'ckeditor5';
import { LegalDocument } from '../../services/legal-document.service';
import { ProtectionGuide } from '../../services/admin-protection-guide.service';
import { ReportDisclaimer } from '../../services/admin-report-disclaimer.service';

export type AdditionalOrientationType = 'INDUSTRIAL_DESIGN' | 'COPYRIGHT' | 'DISTINCTIVE_SIGN' | 'PLANT_BREEDER_CERTIFICATE' | 'TRADE_SECRET';
export type SettingsActionType =
  | 'patentShortDescription' | 'patentEditorReady' | 'patentHtml' | 'savePatent' | 'openPatentPublish' | 'closePatentPublish' | 'activatePatent'
  | 'utilityShortDescription' | 'utilityEditorReady' | 'utilityHtml' | 'saveUtility' | 'openUtilityPublish' | 'closeUtilityPublish' | 'activateUtility'
  | 'additionalShortDescription' | 'additionalEditorReady' | 'additionalHtml' | 'saveAdditional' | 'openAdditionalPublish' | 'closeAdditionalPublish' | 'activateAdditional'
  | 'legalTitle' | 'legalEditorReady' | 'legalHtml' | 'saveLegal' | 'retryDisclaimer'
  | 'disclaimerTitle' | 'disclaimerContent' | 'saveDisclaimer' | 'openDisclaimerPublish' | 'closeDisclaimerPublish' | 'publishDisclaimer';

export interface SettingsAction {
  type: SettingsActionType;
  event?: Event | { editor: { getData(): string } };
  editor?: { setData(data: string): void; getData?(): string };
  sectionType?: AdditionalOrientationType;
}

export interface OrientationViewState {
  shortDescription: string;
  shortDescriptionInvalid: boolean;
  canSave: boolean;
  hasChanges: boolean;
  saving: boolean;
  message: string;
  error: string;
  latestGuide: ProtectionGuide | null;
  savedDraft: ProtectionGuide | null;
  activating: boolean;
  showPublishModal: boolean;
}

export interface AdditionalOrientationView {
  type: AdditionalOrientationType;
  label: string;
  state: OrientationViewState;
}

export interface AdminSettingsViewModel {
  patent: OrientationViewState;
  utility: OrientationViewState;
  additional: AdditionalOrientationView[];
  legal: { loading: boolean; title: string; content: string; saving: boolean; document: LegalDocument | null; message: string; error: string; hasChanges: boolean };
  disclaimer: { loading: boolean; error: string; loaded: boolean; title: string; content: string; message: string; draftError: string; savedDraft: ReportDisclaimer | null; hasChanges: boolean; saving: boolean; activating: boolean; activationMessage: string; activationError: string; showPublishModal: boolean; latest: ReportDisclaimer | null };
}

@Component({
  selector: 'app-admin-settings-section',
  standalone: true,
  imports: [CKEditorModule],
  templateUrl: './admin-settings-section.html',
  styleUrl: './admin-settings-section.css'
})
export class AdminSettingsSectionComponent {
  @Input({ required: true }) model!: AdminSettingsViewModel;
  @Input({ required: true }) editor!: typeof ClassicEditor;
  @Input({ required: true }) additionalEditor!: typeof ClassicEditor;
  @Input({ required: true }) legalEditor!: typeof ClassicEditor;
  @Input({ required: true }) editorConfig!: EditorConfig;
  @Input({ required: true }) legalEditorConfig!: EditorConfig;
  @Input() orientationSections: ReadonlyArray<{ type: AdditionalOrientationType; label: string }> = [];
  @Input() legalDateLabel = (_value: string | null | undefined): string => '';
  @Output() action = new EventEmitter<SettingsAction>();
}
