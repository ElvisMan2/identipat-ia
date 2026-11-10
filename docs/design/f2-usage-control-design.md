# IDENTIPAT-IA — F2-CONSUMO: control de admisión, cuotas y concurrencia

**Estado:** especificación técnica propuesta, pendiente de implementación y validación.  
**Versión:** 0.2 — 2026-11-09  
**Rama de implementación:** `feat/f2-usage-control`  
**Ubicación sugerida en el repositorio:** `docs/design/f2-usage-control-design.md`

> **Convención:** el roadmap vigente reserva F2.0 para evaluación de calidad, F2.1 para privacidad/retención y F2.2 para hardening/operación. Esta iniciativa se denomina **F2-CONSUMO** como frente de trabajo dentro de F2, sin renumerar ni sustituir esas fases del roadmap.

## 1. Propósito y motivación

Evitar que solicitudes concurrentes, automatización o errores de usuario consuman descontroladamente recursos de la IA y de preprocesamiento. Proteger la disponibilidad, el presupuesto de inferencia y las consultas legítimas, conservando trazabilidad y compatibilidad del API.

El código existente (revisado sobre `develop` posterior al PR #34) realiza:

- `POST /identipat-ia/analyses/text`: valida y persiste `Analysis(RECEIVED)` + `AnalysisInput` antes de responder `202`; el `AnalysisWorker` procesa después.
- `POST /identipat-ia/analyses/pdf`: valida archivo, llama a `PreprocessingServiceClient.extractPdf()` y **solo después** persiste el análisis.
- `POST /identipat-ia/analyses/audio`: valida archivo, llama a `PreprocessingServiceClient.transcribeAudio()` y **solo después** persiste el análisis.
- `AnalysisClaimService`: reclama trabajos con PostgreSQL `FOR UPDATE SKIP LOCKED` y lease; controla el **procesamiento**, no la **admisión de nuevas solicitudes**.
- `AnalysisAccessResolver`: distingue STANDARD con sesión/cookie/CSRF y ADMIN con JWT; ambos usan los mismos endpoints de creación.
- `GlobalExceptionHandler`: publica errores JSON con `code`, pero no dispone aún de un contrato específico `Retry-After` para rechazos por capacidad/cuota.

**Regla central:** toda petición costosa debe obtener una reserva durable **antes de iniciar la extracción PDF, la transcripción AUDIO o la persistencia definitiva de un análisis TEXT**. Las solicitudes rechazadas no deben llegar a Python ni al proveedor de IA.

## 2. Política candidata para desarrollo y pruebas

| Control | STANDARD | ADMIN | Motivo |
|---|---:|---:|---|
| Análisis admitidos durante una ventana móvil de 1 hora | 3 | exento provisionalmente | Presupuesto de inferencia |
| Análisis admitidos durante una ventana móvil de 24 horas | 10 | exento provisionalmente | Uso equilibrado |
| Análisis AUDIO admitidos durante una ventana móvil de 24 horas | 3 | exento provisionalmente | Costo de transcripción |
| Análisis activos simultáneos por usuario | 1 | sin tope individual en esta etapa | Evitar duplicaciones STANDARD |
| Análisis globales no terminales, incluidas reservas de preprocesamiento | 8 | comparte capacidad global | Presión sobre backend/LLM |
| Preprocesamientos AUDIO concurrentes | 1 global | comparte capacidad global | Protección de Whisper |
| Límite de tiempo de reserva transitoria | 8 minutos (configurable) | igual | Recuperación de solicitudes abortadas |

Estos valores son **provisionales**. No están aprobados como política institucional de INDECOPI ni representan capacidad garantizada. Las ventanas de 1 h y 24 h son *rolling windows* sobre instantes UTC, no días calendario.

El usuario STANDARD se identifica internamente por `user_id`, **no** por nombre o DNI expuesto en logs. Dado que DNI/CE constituye identificación declarada, no verificación de titularidad, estos límites **no constituyen por sí solos un sistema antifraude**; en una fase posterior se añadirán controles por IP, anti-bot, supervisión y políticas de red.

### Configuración

Exponer valores configurables mediante `@ConfigurationProperties`, perfiles Spring y variables de ambiente; ejemplo de nombres propuestos:

```dotenv
ANALYSIS_ADMISSION_ENABLED=true
ANALYSIS_STANDARD_MAX_PER_HOUR=3
ANALYSIS_STANDARD_MAX_PER_24H=10
ANALYSIS_STANDARD_AUDIO_MAX_PER_24H=3
ANALYSIS_STANDARD_MAX_ACTIVE=1
ANALYSIS_GLOBAL_MAX_ACTIVE=8
ANALYSIS_AUDIO_MAX_PREPROCESSING=1
ANALYSIS_ADMISSION_RESERVATION_TTL=8m
```

Todos los valores deben validarse al arrancar. Si el TTL de reserva no supera el timeout efectivo de preprocesamiento AUDIO más margen de seguridad, **rechazar la configuración al arrancar** en vez de permitir que una reserva expire mientras Whisper sigue activo. No introducir dependencias en Redis durante esta etapa. Con `ADMISSION_ENABLED=false`, conservar el comportamiento existente para tests, pero no usar esta opción como sustituto de cuotas en producción abierta; documentar el flag.

## 3. Semántica del consumo

1. Una reserva `RESERVED` **cuenta provisionalmente** contra cuota, límite por usuario y capacidad global mientras siga vigente. Así se evita sobre-admisión por solicitudes simultáneas que todavía no han recibido `202`.
2. La respuesta `202` implica aceptación y **consumo definitivo de una unidad de cuota**; si luego el worker finaliza en `COMPLETED` o `FAILED`, el consumo sigue contando hasta salir de las ventanas móviles. No se cobra otra cuota por los reintentos internos del worker.
3. Error de validación, fallo de preprocesamiento, fallo de persistencia previo a `202`, cancelación detectada o expiración de reserva: no debe quedar **cuota consumida**. Liberar o invalidar la reserva.
4. La capacidad global y el máximo de un análisis activo STANDARD incluyen: reservas vigentes más análisis aceptados cuyo estado sea `RECEIVED`, `PREPROCESSING` o `ANALYZING`. Los estados `COMPLETED` y `FAILED` liberan el slot automáticamente (sin descontar la cuota histórica).
5. En PDF/AUDIO la admisión ocurre **después de la validación económica local** (MIME, presencia, tamaño, firma cuando aplique) y **antes de llamar a Python**. Fallos semánticos conocidos solo tras Python liberan la reserva.
6. Para AUDIO, limitar a una reserva de preprocesamiento vigente a nivel global. Una petición rechazada por ese límite **no llama a Whisper**.
7. ADMIN tiene exención de cuotas personales en esta iteración, pero participa en la capacidad global y en el límite de preprocesamiento AUDIO; su autenticación JWT y reglas de acceso siguen intactas.
8. No almacenar el DNI/CE, contenido de consultas, binarios de audio/PDF ni IP en las nuevas tablas de admisión. Mantener la política de trazabilidad existente sin ampliarla con PII adicional.

## 4. Modelo durable propuesto

Crear mediante la **siguiente migración Flyway libre** una tabla de admisiones/reservas, por ejemplo `analysis_admissions`:

- `admission_id UUID PRIMARY KEY`.
- `user_id BIGINT NOT NULL`, FK a usuarios si el esquema lo permite.
- `analysis_id UUID NULL UNIQUE`, FK a `analyses` para admisiones aceptadas.
- `input_type VARCHAR(16) NOT NULL` (`TEXT`, `PDF`, `AUDIO`).
- `actor_mode VARCHAR(24) NOT NULL` (`STANDARD_SESSION`, `ADMIN_JWT`).
- `state VARCHAR(16) NOT NULL` (`RESERVED`, `ACCEPTED`, `RELEASED`).
- `reserved_at TIMESTAMPTZ NOT NULL`.
- `expires_at TIMESTAMPTZ NOT NULL` (para `RESERVED`).
- `accepted_at TIMESTAMPTZ NULL`; `released_at TIMESTAMPTZ NULL`.
- Restricciones `CHECK` de consistencia por estado e índices para conteos `(user_id, reserved_at)`, `(input_type, state, reserved_at)` y reservas activas.

Los nombres pueden ajustarse a las convenciones existentes. No colocar el estado de reserva transitoria dentro de `Analysis` si ello rompe el contrato del worker, que solo reclama filas en estados existentes.

**Migración de datos previos:** los análisis existentes todavía activos deben contar contra el límite global/individual después de desplegar F2. Adoptar explícitamente un backfill seguro (por ejemplo, análisis creados dentro de las últimas 24 h o aún no terminales), o un conteo compatible de filas sin admisión. Documentar y probar el criterio elegido; no ignorar datos heredados silenciosamente. Mantener la migración no destructiva y forward-only.

## 5. Atomicidad, carreras y recuperación

Se exige consistencia **entre procesos JVM distintos**. No bastan `synchronized`, `Semaphore`, `AtomicInteger`, contadores HTTP, ni contar filas y luego insertar en transacciones independientes sin exclusión.

Estrategia aceptable para la primera iteración: **transacciones PostgreSQL + `pg_advisory_xact_lock` con clave global fija** para serializar todas las decisiones de admisión (tráfico esperado de piloto: bajo a moderado). Implementar y documentar esta decisión o proponer equivalente verificable con locks persistentes y restricciones. La exclusión global debe terminar al cerrar la **transacción corta**, nunca debe mantenerse durante PDF, Whisper o la llamada LLM.

### Transacción A — adquirir reserva

1. Resolver actor autenticado y consentimiento vigente, validar entrada básica.
2. Abrir transacción corta y obtener lock de admisión en PostgreSQL.
3. Invalidar/ignorar reservas `RESERVED` expiradas.
4. Consultar cuotas y slots incluyendo otras reservas vigentes y análisis aceptados no terminales.
5. Si hay capacidad, insertar admisión `RESERVED` con UUID y plazo suficiente y confirmar transacción.
6. Si no hay capacidad, devolver rechazo sin crear admisión ni invocar Python.

### Trabajo fuera de la transacción

- TEXT: la validación y persistencia pueden agruparse de manera atómica para evitar una reserva visible innecesariamente.
- PDF: extraer texto con el servicio Python.
- AUDIO: transcribir con Whisper.

### Transacción B — confirmar aceptación

1. Obtener lock de admisión y verificar estado `RESERVED` y plazo vigente.
2. Persistir `Analysis` y `AnalysisInput` mediante el servicio existente.
3. En la **misma transacción**, asociar `analysis_id` y cambiar admisión a `ACCEPTED`.
4. Confirmar y devolver `202` con `Location` y el `Retry-After` ya utilizado para polling.

Si algo falla antes de confirmar, deshacer esa transacción y liberar la reserva desde una nueva transacción corta `REQUIRES_NEW` o frontera equivalente; `release` ha de ser idempotente y aplicarse **solo a `RESERVED`**. No capturar una excepción después de confirmar `ACCEPTED` para convertir el análisis en una reserva `RELEASED`.

### Reservas abandonadas

- No se permite confirmar una reserva caducada. Si la transcripción termina tarde, la confirmación falla de manera controlada, sin insertar un análisis fuera de capacidad.
- La limpieza de reservas expiradas debe ser segura tras reinicios; puede hacerse al admitir nuevas solicitudes y opcionalmente mediante tarea programada.
- Es necesario probar la carrera entre `release`, `accept` y `expire`.
- Si un análisis aceptado queda pendiente tras caída del worker, **su slot permanece ocupado** hasta la recuperación/estado terminal; no borrarlo por TTL de reserva. Alertar por antigüedad anómala en una fase de observabilidad.

## 6. Contrato HTTP

Respetar endpoints y respuestas exitosas existentes. Agregar códigos públicos estables:

| Condición | HTTP | Código JSON sugerido | `Retry-After` |
|---|---:|---|---|
| Superó cuota 1 h / 24 h / audio 24 h | 429 | `ANALYSIS_QUOTA_EXCEEDED` | segundos hasta siguiente oportunidad estimada |
| Ya tiene un análisis STANDARD activo | 429 | `ANALYSIS_ALREADY_ACTIVE` | segundos positivos conservadores (p. ej. 10) |
| Capacidad global llena | 503 | `ANALYSIS_CAPACITY_EXCEEDED` | segundos positivos conservadores |
| Whisper/preprocesamiento AUDIO ocupado | 503 | `AUDIO_PREPROCESSING_BUSY` | segundos positivos conservadores |

El cuerpo seguirá el patrón de `GlobalExceptionHandler`: `timestamp`, `status`, `error`, `code`, `message`. No introducir detalles de presupuesto, datos personales, sesiones, tamaño de cola u otros usuarios en errores públicos. Debe enviarse `Retry-After` como encabezado HTTP real para 429/503, sin alterar el `Retry-After` de `202 Accepted` que se emplea para polling.

Conservar: `401` de sesión/JWT, `403` CSRF, `409` consentimiento, `413` tamaño, `415` MIME, `422` validación. Un rechazo por cuota no debe ocultar errores de autorización o validación ya comprobables de forma económica.

## 7. Integración con el frontend

Para **F2-CONSUMO-01** se establece el contrato HTTP/backend; no efectuar rediseños Angular. **F2-CONSUMO-02** mostrará mensajes en español para `429`/`503` y deshabilitará el envío repetido mientras exista una solicitud activa conocida; conservará cookie/CSRF STANDARD y Bearer ADMIN. No introducir historial ni perfil para STANDARD.

Los controles de UI nunca sustituyen la validación del backend.

## 8. Plan de entrega

### F2-CONSUMO-01 — Backend durable (hacer ahora)

- Modelo/migración de admisiones.
- Configuración externalizada y validación de startup.
- Servicio transaccional de reserva/confirmación/liberación.
- Aplicación en TEXT, PDF, AUDIO antes de procesamientos costosos.
- Cuotas STANDARD, un análisis activo STANDARD, capacidad global y límite de Whisper.
- Errores `429/503`, códigos estables y `Retry-After`.
- Tests unitarios, MVC e integración PostgreSQL concurrente.
- Documentación y `.env.example`.

### F2-CONSUMO-02 — Contrato UI e idempotencia

- Manejo de 429/503 y mensajes orientativos en Angular.
- `Idempotency-Key`/reintentos seguros para impedir duplicación por timeout de red; definir identidad del request por usuario, formato y payload hash, retención y conflicto de claves antes de implementarlo.
- Pruebas de navegador STANDARD/ADMIN con sesiones reales.

### F2-CONSUMO-03 — Antiautomatización y observabilidad

- Rate limit por IP con definición segura de IP real/proxy confiable.
- Anti-bot graduado y alertas de abuso; no basar seguridad en DNI/CE sin validación.
- Métricas de admisión/consumo, auditoría saneada y dashboards; investigación de consumo por proveedor.
- Instrumentar los eventos agregados de idempotencia `new_claim`, `replayed_accepted`, `in_progress`,
  `payload_conflict`, `expired_claim_recovered` y `release_before_accept`, sin claves, digests, DNI/CE,
  contenido, credenciales ni metadata técnica sensible.
- Pruebas de carga 20–50 usuarios activos y ajuste de capacidad según resultados reales.

## 9. Matriz mínima de pruebas para F2-CONSUMO-01

1. STANDARD TEXT: primera solicitud válida devuelve `202`, consume cuota y deja `Analysis`/`AnalysisInput` existentes.
2. STANDARD PDF y AUDIO: reservar antes de llamar a Python; cuando se rechaza, verificar mediante spy/mock que **no** se llamó a Python.
3. Aceptaciones exactamente al límite `3/h`, `10/24h` y `3 audio/24h` con reloj fijo; la siguiente da 429 con `Retry-After` positivo.
4. Ventana móvil: al avanzar el reloj fuera de la ventana, vuelve a admitir.
5. Un usuario STANDARD con análisis no terminal obtiene 429; después de `COMPLETED` o `FAILED` el slot se libera, aunque la cuota histórica permanece.
6. Dos solicitudes simultáneas del mismo STANDARD: no se admiten dos; dos usuarios compitiendo por último slot global: solo uno entra.
7. Ocho plazas globales ocupadas: la siguiente obtiene 503, sin análisis adicional; con una terminal, puede entrar otra.
8. Dos AUDIO concurrentes: solo una reserva llega a Whisper; la otra recibe 503 y no consume cuota definitiva.
9. PDF inválido, audio inválido, preprocesamiento que falla o timeout: reserva liberada, no se crea `Analysis` aceptado ni se gasta cuota.
10. Expiración: reserva abandonada se recupera; una confirmación tardía no sobre-admite.
11. ADMIN: conserva JWT y no gasta cuotas STANDARD, pero respeta límites globales y de Whisper.
12. Mantener 401/403/409/413/415/422, `Location` y `Retry-After` de 202, polling, retries internos y reporting.
13. Backfill/compatibilidad con análisis anteriores a la migración.
14. Pruebas PostgreSQL reales con Testcontainers para locks, transacciones y concurrencia; evitar `Thread.sleep()` flakey y usar barreras de sincronización.
15. Test suite completa Java y smoke Docker integrado; CI verde tras push/PR.

## 10. Criterios de aceptación y exclusiones

**Aceptar F2-CONSUMO-01 solamente si:**

- Ninguna solicitud rechazada por cuota/capacidad invoca Whisper, extracción PDF o LLM.
- Ninguna solicitud inválida o con preprocesamiento fallido queda cobrada como aceptación.
- Los límites se cumplen bajo dos instancias backend concurrentes contra el mismo PostgreSQL, no solo en ejecución secuencial.
- No se pierde aislamiento STANDARD/ADMIN, integridad de migraciones ni trazabilidad existente.
- La suite de tests y el CI terminan en verde; se documentan incidentes intermitentes separados.

**Fuera de alcance ahora:** rediseño de pantallas; login STANDARD; Redis; cambio de proveedor IA; migrar Whisper; modificar prompts y diagnóstico; eliminar datos históricos; desplegar manualmente en PREPROD; tocar volúmenes existentes; redefinir la política jurídica de retención.

## 11. Riesgos y decisiones pendientes

- **Cuotas institucionales:** números por confirmar después de medir uso real y acordar con INDECOPI.
- **DNI/CE no verificado:** la identidad declarada no impide suplantación o evasión de cuotas.
- **Retries HTTP sin idempotencia:** pueden dar duplicaciones en un resultado recibido pero no observado; F2-CONSUMO-02 debe resolverlo antes de exposición pública.
- **TTL de reserva:** calibrar al timeout real de AUDIO, garantizar cierre controlado.
- **Legacy:** especificar backfill de análisis activos y últimos 24 h; sin ello se evade la cuota al desplegar.
- **Flakiness preexistente de Angular/CKEditor:** tratar en un cambio separado para no contaminar esta implementación.
- **Operación:** no aplicar migraciones ni redeploy a PREPROD hasta aprobar tests de integración y revisión de seguridad; nunca ejecutar `docker compose down -v`.

## 12. Archivos de referencia en el repositorio

- `backend/src/main/java/com/mnk/identipatia/analysis/controller/AnalysisController.java`
- `backend/src/main/java/com/mnk/identipatia/analysis/service/AnalysisApplicationService.java`
- `backend/src/main/java/com/mnk/identipatia/analysis/service/AnalysisCreationPersistenceService.java`
- `backend/src/main/java/com/mnk/identipatia/analysis/service/AnalysisAccessResolver.java`
- `backend/src/main/java/com/mnk/identipatia/analysis/worker/AnalysisClaimService.java`
- `backend/src/main/java/com/mnk/identipatia/analysis/worker/AnalysisAttemptService.java`
- `backend/src/main/java/com/mnk/identipatia/analysis/model/Analysis.java`
- `backend/src/main/java/com/mnk/identipatia/analysis/model/AnalysisStatus.java`
- `backend/src/main/java/com/mnk/identipatia/advice/GlobalExceptionHandler.java`
- `backend/src/main/resources/db/migration/` (identificar última versión libre)
- `frontend/src/app/services/standard-analysis.service.ts`
- `.env.example`, `backend/src/main/resources/application.yml`, `application-dev.yml`, `application-prod.yml`
- `docs/roadmap-funcional.md` (no renumerar F2.0/F2.1/F2.2/F2.3)

## 13. Decisiones de implementación F2-CONSUMO-01

- La migración `V10__analysis_admissions.sql` crea la tabla durable y hace backfill de todo análisis no terminal y de los análisis creados durante las últimas 24 horas. Los activos históricos conservan su slot aunque sean más antiguos; los terminales antiguos no consumen una ventana ya vencida.
- Todas las operaciones `reserve`, `confirm`, `release` y `expire` se serializan con `pg_advisory_xact_lock(4942446500001)` dentro de transacciones cortas. El lock no se mantiene durante extracción PDF ni transcripción AUDIO.
- La confirmación persiste `Analysis`, `AnalysisInput` y la transición a `ACCEPTED` en una sola transacción. La liberación usa una transacción nueva, es idempotente y solo modifica `RESERVED`.
- `ANALYSIS_ADMISSION_ENABLED=false` existe únicamente para compatibilidad controlada de tests. La configuración normal lo habilita y valida que el TTL sea estrictamente mayor que `PREPROCESSING_AUDIO_TIMEOUT` más el margen de seguridad.
