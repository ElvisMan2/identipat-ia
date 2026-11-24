# PDF multimodal con Gemini/Vertex AI

## Alcance

`POST /analyses/pdf` conserva su contrato público, autorización, consentimiento, cuotas e
idempotencia. Para análisis nuevos, el PDF original se valida mediante el servicio Python, se
persiste de forma durable en PostgreSQL y el worker lo adjunta como `application/pdf` a Gemini.
`TEXT` y `AUDIO` mantienen el prompt textual `intellectual-property-analysis/0.3.1`; PDF usa
`intellectual-property-analysis/0.3.2` y el mismo schema `analysis-result/3.0`.

No hay fallback PDF→texto. La admisión responde `503 PDF_MULTIMODAL_PROVIDER_REQUIRED` cuando el
provider activo no declara soporte PDF. Los análisis PDF históricos que no tengan binario fallan
de forma controlada con `ANALYSIS_DOCUMENT_UNAVAILABLE`; sus resultados históricos siguen siendo
legibles.

## Flujo y persistencia

1. Java valida multipart, MIME, firma y 10 MiB antes de llamar a Python.
2. `POST /v1/pdf/inspect` valida estructura, cifrado, corrupción y cantidad de páginas con
   `pypdf`. Devuelve `pageCount`, `hasTextLayer`, `characterCount` y `extractionMethod`, pero no el
   texto del documento. Un PDF válido sin capa textual se acepta.
3. La confirmación de admisión crea `analyses`, `analysis_inputs`, `analysis_pdf_documents` y la
   aceptación de idempotencia dentro de una sola transacción. Un replay devuelve el mismo análisis
   y no duplica el `BYTEA`.
4. En cada intento, el worker vuelve a leer el documento por `analysis_id`, compara tamaño, MIME y
   SHA-256 contra el binario y `source_metadata`, crea un `AiAttachment` inmutable y cierra la
   transacción antes de invocar al proveedor.
5. Gemini recibe primero las instrucciones de usuario y luego un `Part.fromBytes(...,
   "application/pdf")`. El snapshot y `ai_invocations.request_parameters` contienen solo metadata
   segura (MIME, tamaño, páginas y SHA-256), nunca bytes ni base64.

La migración `V15__analysis_pdf_documents.sql` crea una fila 1:1 por análisis con FK y `ON DELETE
CASCADE`. Sus constraints validan MIME, tamaño real y formato del SHA-256.

## Seguridad y retención

- No se registran contenidos, texto extraído, base64, prompts renderizados ni respuestas completas
  en logs. La API STANDARD no expone metadata técnica de la invocación.
- El acceso a `analysis_pdf_documents` queda dentro del rol PostgreSQL del backend. En ambientes
  compartidos, limitar el rol y las copias de seguridad a operadores autorizados.
- La protección en reposo depende del cifrado administrado del volumen/base de datos y de backups;
  V15 no introduce criptografía a nivel de columna. Debe validarse con la política institucional.
- `ANALYSIS_PDF_RETENTION` vale `7d` por defecto y
  `ANALYSIS_PDF_CLEANUP_INTERVAL` vale `1h`. La limpieza solo borra documentos de análisis
  `COMPLETED`/`FAILED` cuyo `updated_at` superó la ventana; nunca toca `RECEIVED`, `PREPROCESSING` o
  `ANALYZING`, por lo que retries y recuperación de lease conservan el archivo.
- Siete días es una decisión conservadora de DEV/QA, no una política institucional aprobada. Antes
  de producción deben aprobarse retención, backups, RPO/RTO, cifrado y atención de solicitudes de
  eliminación.

## Configuración local

```text
IDENTIPAT_AI_ENABLED=true
IDENTIPAT_AI_PROVIDER=gemini
GOOGLE_APPLICATION_CREDENTIALS=<ruta privada fuera del repositorio>
GOOGLE_CLOUD_PROJECT=<proyecto>
GOOGLE_CLOUD_LOCATION=global
GEMINI_MODEL=gemini-3.8-flash
GEMINI_API_VERSION=v1
GEMINI_TIMEOUT=60s
ANALYSIS_LEASE_DURATION=120s
ANALYSIS_PDF_RETENTION=7d
ANALYSIS_PDF_CLEANUP_INTERVAL=1h
```

El lease debe seguir siendo mayor que el timeout del proveedor. No confirmar credenciales ni el
archivo indicado por `GOOGLE_APPLICATION_CREDENTIALS`.

## Pruebas

Sin consumo real:

```powershell
cd preprocessing-service
python -m pytest
python -m ruff check .

cd ..\backend
.\mvnw.cmd test
```

El smoke real es opt-in y tiene costo. Usa un PDF sintético sin datos personales:

```powershell
$env:RUN_GEMINI_LIVE_SMOKE='true'
$env:GEMINI_SAFE_DIAGNOSTICS='true'
.\mvnw.cmd '-Dtest=GeminiLiveSmokeIT' test
```

Para probar `FINALISTAS IDF 2026-C.pdf`, hacerlo únicamente en un ambiente autorizado: aplicar V15,
arrancar Python y Java con Gemini/ADC, cargar el archivo por `POST /analyses/pdf` con el contexto
ADMIN o STANDARD correspondiente y consultar el `Location` hasta `COMPLETED`. Verificar internamente
que el SHA-256 de `analysis_pdf_documents` coincide con el archivo y que la invocación registra
prompt `0.3.2`, `attachmentCount=1` y schema `3.0`. No copiar el PDF al repositorio, fixtures, logs ni
reporte de pruebas.

## Limitaciones y rollback

- No se agregó OCR: un escaneado se acepta porque Gemini recibe el original; Python solo informa que
  no hay capa textual.
- El modelo puede encontrar varias invenciones en un documento. El prompt exige advertir la
  limitación y no fabricar una unidad, pero esta fase no incorpora selección interactiva.
- No hay routing por complejidad, Context Caching, RAG, Flash-Lite ni comparación de modelos.
- El smoke pagado depende de disponibilidad, permisos ADC, cuota y soporte efectivo del modelo.

El rollback de aplicación debe hacerse manteniendo V15: las migraciones Flyway no se revierten ni se
edita `flyway_schema_history`. Un binario anterior ignora la tabla adicional. Si la organización
decide eliminarla, debe hacerse después de backup y aprobación DBA mediante una migración posterior,
nunca con un `DROP` manual durante el despliegue.
