# Diseño de reporting F1.7

**Versión pública:** `diagnostic-report/1.0`  
**Estado:** implementado  
**Última actualización:** 5 de noviembre de 2026

## Fuente única y límites

El reporte se construye desde `Analysis`, `analysis_results`, `analysis_report_contexts` y
`report_disclaimers`. No llama a `GenerativeAiProvider`, no altera `analysis-result/1.0` o 2.0 y no
reclasifica modalidades. Tampoco devuelve PII, sesión, consentimiento, prompt, respuestas técnicas,
proveedor, modelo ni tokens.

## Disclaimer y contexto histórico

`ReportDisclaimer` admite `DRAFT → ACTIVE → RETIRED`; solo DRAFT es editable y no existe DELETE.
Las versiones se asignan con `report_disclaimer_version_seq`. La base garantiza como máximo un
ACTIVE mediante el índice único parcial `uk_report_disclaimers_single_active`.

La activación y `AnalysisAttemptService.succeed` bloquean todas las versiones en orden estable antes
de resolver el ACTIVE. La activación hace flush del retiro antes de la promoción para respetar el
índice parcial. `succeed` persiste el resultado, crea `AnalysisReportContext` y recién entonces marca
el análisis `COMPLETED`, todo en una transacción. La migración V5 enlaza los COMPLETED previos al seed
SYSTEM v1; no modifica `result_json`.

## Presentación ciudadana

`DiagnosticReportAssemblerV1` conserva de forma explícita el contrato histórico y
`DiagnosticReportAssemblerV2` proyecta el contrato vigente. Las modalidades usan nombres en español;
`LIKELY`, `POSSIBLE` y `UNLIKELY` se muestran como “Se relaciona claramente”, “Podría corresponder” y
“La relación es limitada”. La orientación y la nota de applicability son texto controlado por Java.

El resultado persistido se conserva intacto en `analysis_results`. Al ensamblar el
`DiagnosticReport`, la capa de presentación sustituye solo tokens internos completos y conocidos de
modalidad, applicability, screening, recursos genéticos, instrumentos legales y patentabilidad
histórica en todo texto narrativo dirigido al usuario: resumen, objeto protegido, justificaciones,
observaciones, advertencias, screening y recursos genéticos. Las construcciones `MODALIDAD
(APPLICABILITY)` se expresan de forma natural con una raya; los tokens desconocidos, las coincidencias
parciales y el texto jurídico natural no se alteran. Los campos estructurados conservan sus enums
canónicos. Esta proyección ciudadana es compartida por JSON, PDF y reporting operacional.

`observations` se presenta como “Aspectos a considerar”; `warnings` como “Advertencias del caso”. El
disclaimer institucional y “Alcance del análisis” son bloques distintos y nunca se agregan a warnings.

## Bloques transversales

`patentScreening` y `geneticResourceAccess` son bloques transversales de `DiagnosticReport`,
independientes de `protectionOptions`. Nunca se anidan dentro de una modalidad y cada uno se presenta
como máximo una vez. El contrato vigente `diagnostic-report/1.0` se modificó directamente porque aún
no había sido presentado ni liberado como contrato externo ante INDECOPI.

Cuando `patentScreening.applicable` es `true`, el reporte incluye el bloque raíz con título y contexto
neutrales, sin depender de que exista patente de invención o modelo de utilidad. Si es `false`, el
bloque raíz es `null`. Los casos visibles mantienen el detalle de artículos 15 y 20, sus coincidencias,
labels y la nota controlada de alcance.

Cuando `geneticResourceAccess.assessment` es `POTENTIALLY_REQUIRED` o
`INSUFFICIENT_INFORMATION`, el reporte incluye el bloque raíz. Para `NOT_INDICATED` es `null`. Esta
decisión no depende de patente de invención ni de certificado de obtentor, y la presentación ya no
expone un campo `placement`.

## JSON, PDF y seguridad

Los GET `/analyses/{id}/report` y `/report.pdf` usan `AnalysisAccessResolver`: STANDARD requiere la
misma `session_id`; ADMIN requiere el mismo `user_id` y `session_id IS NULL`; un Bearer presente nunca
hace fallback a cookie. Solo `COMPLETED` entrega reporte.

El PDF A4 se genera completamente en memoria con Apache PDFBox 3.0.3. Usa Noto Sans para texto y
Noto Sans Math como fallback de símbolos Unicode —incluida la flecha `U+2192`—, ambas empaquetadas en
`backend/src/main/resources/fonts`. Las fuentes se distribuyen bajo SIL Open Font License 1.1, cuyo texto
se conserva en `OFL.txt`. El renderer selecciona la fuente por punto de código, pagina, ajusta líneas y
no crea archivos temporales. El layout reserva espacio mínimo para evitar títulos huérfanos, mantiene
juntos labels con el inicio de su contenido, procura conservar el cierre del screening con su nota de
alcance y mueve a la página siguiente el bloque corto completo de recursos genéticos cuando no cabe en
el espacio restante. Si ese bloque supera una página útil, permite su división natural. Bandas suaves,
badges semánticos y metadata compacta aportan jerarquía sin cambiar el contenido.

## Errores

- `REPORT_NOT_READY` / `REPORT_UNAVAILABLE`: 409.
- `REPORT_UNSUPPORTED_RESULT_VERSION`: 409.
- `REPORT_CONTEXT_UNAVAILABLE` / `REPORT_GENERATION_FAILED`: 500.
- Recurso inexistente o ajeno: `ANALYSIS_NOT_FOUND`, 404.
