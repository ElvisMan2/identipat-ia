# Guía de despliegue de producción — IDENTIPAT-IA

- **Estado:** runbook propuesto, pendiente de validación/aprobación OTI.
- **Base de código:** `c6875dbf26417bbfcf44a57546408a3f2110436a` (`develop`, inspeccionado el 13-11-2026).
- **Uso:** completar los marcadores antes de emitir una orden de cambio. No es una orden ejecutable ni confirma que exista producción.

## Marcadores y límites

- `[PENDIENTE VALIDACIÓN OTI]`: dato o decisión que no fija el repositorio. No sustituir por valores de QA.
- `[PLANTILLA NO EJECUTABLE]`: ejemplo que depende de parámetros/infraestructura todavía no confirmados. No copiar al host ni ejecutar hasta que OTI/DBA autorice y complete cada parámetro.
- Las comprobaciones marcadas **automatizadas** están definidas en CI/código. **Manual** significa que el operador/QA debe realizarlas y adjuntar evidencia; no implica que ya se hayan realizado.
- Nunca adjuntar salida de `docker compose config` ni valores de `.env`/secret manager. No hacer `down -v`, borrar volúmenes, migrar/restaurar fuera del plan aprobado ni ejecutar una migración manual de producción.

## Datos del cambio por completar

| Dato | Valor aprobado |
|---|---|
| Solicitud, versión visible, ventana y entorno | [PENDIENTE VALIDACIÓN OTI] |
| Servidor(es), SO/arquitectura y ruta del proyecto | [PENDIENTE VALIDACIÓN OTI] |
| DNS/FQDN, proxy HTTPS, certificados y dueño | [PENDIENTE VALIDACIÓN OTI] |
| Archivo Compose aprobado, nombre de proyecto/red, nombres de servicio y volúmenes | [PENDIENTE VALIDACIÓN OTI] |
| PostgreSQL: host, versión, base/esquema, owner y DBA | [PENDIENTE VALIDACIÓN OTI] |
| Backup: mecanismo, destino cifrado, retención, RPO/RTO y prueba de restore | [PENDIENTE VALIDACIÓN OTI] |
| Digest app, digest Python y SHA de origen | [PENDIENTE VALIDACIÓN OTI] |
| Custodios de secretos, validadores y responsable de reversa | [PENDIENTE VALIDACIÓN OTI] |

## 1. Prerrequisitos y criterio de autorización

1. OTI aprueba la solicitud, ventana, entorno y plan de comunicación. Asignar responsable técnico de aplicación, DBA, infraestructura/proxy, seguridad/privacidad y validador funcional.
2. Confirmar que el target y su archivo Compose estén versionados o aprobados fuera del repo y revisados. Los únicos Compose del repo son desarrollo raíz y QA (`deploy/qa/compose.yml`); **ninguno está autorizado como Compose de producción**.
3. Confirmar el espacio de disco/RAM/CPU real para PostgreSQL, Java y Python. La imagen Python necesita memoria/CPU y espacio para el modelo local `base`; los límites del Compose QA son únicamente valores iniciales de laboratorio.
4. Definir conectividad saliente a GHCR y al repositorio del modelo si no se provisiona offline. El runtime de OpenAI requiere egress HTTPS permitido por política. La URL de Python debe ser privada entre Java y Python; no publicar 8090.
5. Aprobar los nombres de las variables y cargar sus valores en mecanismo institucional de secretos. No guardar credenciales en Git, ticket, captura o log.
6. Confirmar política legal y contractual para entradas de usuarios y proveedor IA (incluye contenido de texto y transcripciones). Sin aprobación escrita, **NO-GO para procesar información real**.

## 2. Preparación del release y de las imágenes

**Automatizado por CI para el SHA que se ejecute:** backend tests/empaquetado con Maven Wrapper; frontend tests/build estático; Python pytest/Ruff/build Docker/health general; imagen integrada y smoke test con PostgreSQL efímero. Los eventos `push` publican dos imágenes por tag SHA y presentan sus digests en el resumen de Actions. CI no despliega.

**Manual previo al cambio:**

1. Elegir un SHA completo aprobado (no una rama móvil). Consultar la ejecución CI de ese mismo SHA y confirmar todos los checks requeridos en verde; guardar URL/ID de ejecución y aprobador.
2. Desde el resumen de publicación, registrar el digest inmutable de **cada** imagen y verificar que el tag SHA y los paquetes correspondan al commit aprobado. No inventar digests ni derivarlos de un tag flotante.
3. Confirmar acceso de solo lectura del host a ambos paquetes, plataforma/arquitectura compatible y política de escaneo/firma que OTI requiera. La firma, SBOM o escaneo no están acreditados por el workflow revisado; si la institución los exige, son puerta adicional de GO.
4. Mantener juntos commit, ejecución CI, URI por digest, cambio aprobado y plan de rollback. Los directorios `target`/`dist` locales no son artefactos de release.

**[PLANTILLA NO EJECUTABLE — requiere validar URI, digest, host, acceso GHCR y plataforma]**

```sh
# Reemplazar cada marcador solo con el dato aprobado de la ejecución CI.
docker pull ghcr.io/elvisman2/identipat-ia@sha256:<DIGEST_APP_APROBADO>
docker pull ghcr.io/elvisman2/identipat-preprocessing@sha256:<DIGEST_PYTHON_APROBADO>
docker image inspect ghcr.io/elvisman2/identipat-ia@sha256:<DIGEST_APP_APROBADO>
docker image inspect ghcr.io/elvisman2/identipat-preprocessing@sha256:<DIGEST_PYTHON_APROBADO>
```

No pegar credenciales de GHCR en comandos con historial. El método de login del host queda [PENDIENTE VALIDACIÓN OTI].

## 3. Preparación operativa y respaldo

1. Registrar versiones/digests actualmente activos, estado de servicios, espacio, alertas y ruta de configuración aprobada. Proteger el registro de datos sensibles.
2. DBA identifica base y esquema correctos, verifica que no haya otra migración/operación incompatible en curso y obtiene un backup consistente por el método institucional aprobado. Registrar hora, identificador de backup, destino, cifrado, retención y persona responsable, sin copiar credenciales.
3. DBA acredita que el backup terminó correctamente y que la restauración fue probada según procedimiento institucional (preferiblemente en una instancia aislada). Definir RPO/RTO y quién puede autorizarla. Un archivo generado sin prueba no es evidencia de restaurabilidad.
4. Preparar respaldo protegido de configuración anterior sin incluir secretos en el ticket. Asegurar que se puede recuperar el valor de `ANALYSIS_IDEMPOTENCY_HMAC_SECRET` y demás credenciales desde el secret manager.
5. Aprobar las relaciones de temporizadores: `ANALYSIS_ADMISSION_RESERVATION_TTL` debe superar `PREPROCESSING_AUDIO_TIMEOUT` más `ANALYSIS_ADMISSION_RESERVATION_SAFETY_MARGIN`; `ANALYSIS_IDEMPOTENCY_CLAIM_TTL` debe superar el TTL de reserva; `ANALYSIS_IDEMPOTENCY_RETENTION` debe ser mayor que el claim TTL y también mayor que 24 horas, según la guía QA. Mantener el HMAC estable en todas las réplicas y entre releases durante retención; una rotación prematura rompe la detección de replays aún vigentes.
6. Decidir si `ANALYSIS_IDEMPOTENCY_REQUIRED` se activa. Es opcional por defecto en el Compose QA; validar clientes y header `Idempotency-Key` antes de exigirlo.

**[PLANTILLA NO EJECUTABLE — completar solo con procedimiento DBA y destino aprobados]**

```text
Backup PostgreSQL: [PENDIENTE VALIDACIÓN OTI: herramienta institucional, instancia/base/esquema,
                      destino cifrado, identificador, retención, verificación y DBA responsable]
Inspección pre-migración: [PENDIENTE VALIDACIÓN OTI: consulta/versionado Flyway y compatibilidad]
```

No se incluye comando de backup o restore porque se desconocen la instancia, permisos y herramientas; no improvisar sobre producción.

## 4. Configuración y secuencia de arranque

### 4.1 Preparar configuración aprobada

El archivo final, path, nombres de red y volúmenes quedan [PENDIENTE VALIDACIÓN OTI]. Configurar solo nombres aprobados: `SPRING_PROFILES_ACTIVE`, `SERVER_PORT`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `STANDARD_SESSION_PEPPER`, `CORS_ALLOWED_ORIGINS`, `IDENTIPAT_AI_ENABLED`, `IDENTIPAT_AI_PROVIDER`, `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_TIMEOUT`, `PREPROCESSING_SERVICE_BASE_URL`, límites de análisis, admisión, worker e idempotencia y variables `PREPROCESSING_*`/`HF_HOME` necesarias.

El perfil `prod` exige explícitamente configuración sensible/externa, fuerza cookie de sesión segura, usa Flyway sin baseline automático y `ddl-auto=validate`; Swagger queda deshabilitado. `SERVER_FORWARD_HEADERS_STRATEGY` está en la composición de QA pero no es una topología productiva demostrada: OTI debe validar proxy y cabeceras seguras para que esquema/host externos sean correctos. Configurar CORS con el origen institucional exacto, nunca wildcard.

**[PLANTILLA NO EJECUTABLE — no existe un Compose productivo aprobado en este repo]**

```sh
# Completar con el path, env-file y Compose aprobados. No ejecutar `config` sin evitar
# que valores secretos lleguen a salida, logs o capturas.
docker compose --env-file <ENV_FILE_PROTEGIDO_APROBADO> -f <COMPOSE_PRODUCCION_APROBADO> config --quiet
```

Si la plataforma Compose no admite una validación silenciosa equivalente, usar la herramienta aprobada para validar el modelo y revisar nombres requeridos sin exponer la salida resuelta. La plantilla no identifica ni autoriza un archivo real.

### 4.2 Arrancar Python y esperar modelo

1. Descargar/iniciar primero el servicio de preprocesamiento con digest Python aprobado. Usar únicamente la red privada aprobada, sin publicar el puerto 8090 al host ni a Internet.
2. Confirmar `GET /health` responde healthy. Este endpoint solo prueba que FastAPI atiende; **no** confirma audio.
3. Repetir `GET /v1/audio/health` hasta que devuelva `200` y estado `UP`. Un `503 AUDIO_MODEL_NOT_READY` significa que el modelo no terminó de cargar. Si `/models` está vacío, determinar previamente cómo se permite descargar/provisionar el modelo; el Dockerfile no lo incluye. Verificar métricas de memoria, reinicios y espacio durante warm-up.
4. En ambiente aprobado y con archivo sintético/consentido, ejecutar una transcripción de control y cotejar que hay respuesta útil. No cargar una descripción real como prueba técnica sin la aprobación de datos. Los endpoints no se comprueban con el health general.

**[PLANTILLA NO EJECUTABLE — la ruta Compose, nombre de servicio y acceso interno están pendientes]**

```text
Consultar desde el contenedor Python: GET http://127.0.0.1:8090/health
Consultar desde el contenedor Python: GET http://127.0.0.1:8090/v1/audio/health
Ejecutar POST /v1/audio/transcribe con fixture aprobado: [cliente y fixture pendientes]
```

5. Verificar extracción de PDF de texto digital con PDF sintético autorizado y caso rechazado de PDF escaneado/no extraíble. F1.5 no implementa OCR. El binario no se almacena por el servicio Python; texto extraído se procesa después en Java.

### 4.3 Arrancar Java y migraciones

1. Confirmar disponibilidad privada de PostgreSQL y conectividad desde el contenedor Java, destino/roles aprobados y backup registrado.
2. Arrancar la aplicación integrada con el digest aprobado después de que Python esté saludable y Whisper listo. Java sirve Angular y API en `/identipat-ia`, puerto interno 8082.
3. El arranque de Spring ejecuta Flyway antes de Hibernate. Verificar logs de migración sin adjuntar entradas de usuario, prompts, respuestas ni credenciales. Hibernate debe completar `validate` sin diferencias. No ejecutar `flyway clean`, scripts manuales ni migración aislada sobre producción.
4. Inspeccionar el resultado de V13 y compatibilidad del esquema con el release. `V13__drop_users_phone.sql` elimina una columna y no tiene una reversa automática demostrada. Si no está acreditada la compatibilidad del binario previo, mantener la aplicación detenida y activar el plan aprobado por DBA; no reintroducir datos o restaurar la misma base de forma automática.

**[PLANTILLA NO EJECUTABLE — completar con referencias productivas aprobadas]**

```text
Arranque Java: [orquestador aprobado, servicio, digest app y configuración externa pendientes]
Validación Flyway: [log/consulta de solo lectura aprobada por DBA; no recoger datos sensibles]
```

### 4.4 Exponer solo mediante HTTPS

Proxy inverso, ruta pública, certificado, cabeceras forwarded, DNS y reglas de firewall están [PENDIENTE VALIDACIÓN OTI]. Terminar TLS en el proxy institucional aprobado y reenviar al servicio Java por red autorizada; no publicar Python ni PostgreSQL. Desde cliente autorizado confirmar certificado, cadena, hostname, redirección/HTTPS, cabeceras de seguridad, cookie `Secure`, CORS y carga de Angular bajo `/identipat-ia/`. No dar por hecho que un puerto localhost/QA es el endpoint productivo.

## 5. Verificación de servicio

Guardar evidencia con fecha/hora, ambiente, SHA y digests. Usar cuentas de prueba y contenido sintético; ocultar identificadores, cookies, JWT, cuerpos, texto, audio, prompts y respuestas en capturas/logs.

| Caso | Modo de verificación | Criterio de aceptación |
|---|---|---|
| Integridad del release | Automatizada en CI + revisión manual de URL del run/digests | Checks del SHA elegido verdes; dos URI `@sha256:...` registradas y coincidentes con el run. |
| UI/API por HTTPS | Manual desde red de usuario autorizada | Ruta `/identipat-ia/`, assets y ruta SPA funcionan; TLS válido; endpoint API desconocido da error API y no devuelve index SPA. |
| Seguridad STANDARD/ADMIN | Manual con cuentas de prueba; complementada por tests CI | STANDARD sin login/JWT; sesión activa y consentimiento requerido; acceso a análisis solo dentro de su sesión. ADMIN requiere JWT y funciones admin devuelven no autorizado sin credencial. No usar DNI/CE como contraseña. |
| Consentimiento y disclaimer | Manual y evidencias funcionales | Consentimiento de tratamiento aparece/registra de forma separada del disclaimer orientativo; versión legal aprobada corresponde al entorno. |
| Texto | Manual con entrada sintética y autorización para IA | Aceptación de análisis, estado asíncrono y resultado estructurado; trazas no contienen texto/respuesta. Llamada real a OpenAI solo después de aprobación de proveedor y credenciales. |
| PDF | Manual con PDF digital sintético | Extracción y análisis completan; caso escaneado no extraíble se rechaza con error controlado; verificar descarga de reporte PDF. |
| Audio | Automatizada en endpoint de readiness + manual de transcripción | `/v1/audio/health` `UP`; audio sintético español transcribe y produce análisis; medir latencia/recursos. `/health` sola no es suficiente. |
| Persistencia/Flyway | Manual por DBA + observación de log saneado | BD/base/esquema correctos, migraciones esperadas y Hibernate `validate` exitoso; backup previo comprobado. |
| OpenAI y política de datos | Manual con responsable de seguridad/privacidad | Credencial administrada, conectividad/modelo/costos y política contractual aprobados; evidencia técnica sin contenido enviado ni secreto. |
| Monitoreo y estabilidad | Manual | Sin OOM/restarts, errores Flyway, cola estancada o alertas de capacidad; health y métricas dentro de umbrales acordados. |

El smoke de CI no cubre casos funcionales completos, llamada real al LLM, carga de Whisper, proxy TLS ni datos institucionales. No registrar como realizadas pruebas que solo existen en código.

## 6. Criterios GO / NO-GO

### GO solo si

- Aprobación de cambio, usuarios de prueba, responsables y ventana documentados.
- SHA candidato único con cuatro checks requeridos de CI aprobados y dos digests inmutables registrados.
- Compose/host/red/DNS/proxy/certificados/capacidad aprobados y configuración validada sin revelar secretos.
- DBA confirma destino, permisos, backup/restore y plan de V1–V13; compatibilidad V13 revisada para rollback.
- Whisper listo y transcripción de control aprobada; Java, PDF, texto, reporte, consentimiento y flujos de seguridad verificados en QA/entorno autorizado.
- Aprobación institucional y contractual del proveedor IA, datos, retención y contenido legal vigente.
- Plan de monitoreo, comunicación y reversa aceptado por roles responsables.

### NO-GO si

Falta cualquiera de las aprobaciones anteriores; el digest no corresponde al commit; hay error de migración/validación; `/v1/audio/health` no está listo; el backup no es verificable; no se conoce a qué datos enviaría OpenAI; falla TLS/CORS/cookie; hay OOM/restarts; o no existe plan de reversa compatible con el esquema.

## 7. Monitoreo y cierre

Durante y después del cambio, revisar estados de contenedores, reinicios/OOM, uso de memoria/CPU/disco (incluido volumen de Whisper), disponibilidad de PostgreSQL, estado de Flyway, errores del proveedor, cola durable de análisis, latencia y tasa de fallos. Alertas/umbrales, herramienta y guardia están [PENDIENTE VALIDACIÓN OTI]. Logs deben quedar saneados: nunca copiar entradas, prompts, respuestas, transcripciones, análisis completos, tokens ni metadatos privados del proveedor a tickets. Registrar digest y eventos operativos sin payload. Cerrar con resultado de verificación, incidentes, responsable y notificación a solicitantes.

## 8. Marcha atrás prudente

1. Declarar NO-GO/incidente ante degradación; detener admisión de pruebas/usuarios según procedimiento operativo aprobado, preservar logs saneados y registrar digests/estado. No borrar datos ni volúmenes.
2. Si el problema está aislado a Python y no hay cambio de esquema, restaurar su digest anterior manteniendo el caché de modelo. Si es Java, usar solo imagen anterior cuyo código sea compatible con el esquema actual; volver el tag/digest de configuración al valor inmutable anterior y validar en el mismo target autorizado.
3. Si Flyway avanzó y el binario anterior falla `ddl-auto=validate` (riesgo conocido por V13), no forzar downgrade de app ni editar `flyway_schema_history`. Mantener el servicio en estado seguro y solicitar decisión DBA: corrección hacia adelante compatible o recuperación aislada desde backup verificado, con autorización, RPO/RTO y aceptación explícita de pérdida de cambios posteriores al backup.
4. Una restauración no es el rollback por defecto y no debe ser destructiva ni automática. OTI/DBA define si restaura a instancia separada, valida consistencia y solo entonces conmuta endpoint/red. No sobrescribir la base activa sin plan aprobado.
5. Revalidar health, seguridad, análisis de prueba, reportes, métricas y monitoreo; comunicar cierre. Documentar digest final y causa.

### Referencias del repositorio

[Auditoría y hallazgos](./auditoria-pase-produccion.md) · [insumos F-OTI-02](./insumos-f-oti-02.md) · [migraciones](../development/database-migrations.md) · [preprocesamiento](../development/preprocessing-service.md) · [CI](../development/continuous-integration.md) · [configuración de QA (solo referencia QA)](../../deploy/qa/README.md).
