# Diagnóstico del proyecto Identipat IA

**Fecha de revisión:** 10 de septiembre de 2026
**Alcance:** inspección estática del repositorio y verificaciones locales de backend, frontend, persistencia, seguridad y automatización. No se modificó el código de la aplicación.

## Resumen ejecutivo

Identipat IA es una aplicación de gestión de usuarios compuesta por una API Java/Spring Boot y una interfaz Angular. Desde el diagnóstico anterior, la persistencia fue migrada de H2 a PostgreSQL 16, se añadió un entorno local con Docker Compose y pgAdmin, y se incorporó una colección de 25 solicitudes para probar la API. El build de desarrollo del frontend se completa correctamente.

El proyecto todavía no está listo para exponerse fuera de un entorno controlado. El riesgo principal es de autorización: cualquier cliente puede crear un usuario de tipo `ADMIN` con una contraseña elegida, iniciar sesión y obtener un JWT. También permanecen públicos la actualización general de usuarios y la consulta por DOI, que devuelve datos personales. La migración a PostgreSQL mejora la persistencia, pero aún depende de `ddl-auto=update`, credenciales de desarrollo por defecto y una instancia local externa para la prueba de contexto.

## Arquitectura actual

| Capa | Tecnología / estado |
| --- | --- |
| Backend | Java 21 declarado, Spring Boot 3.2.12, Maven y API REST bajo `/identipat-ia` |
| Persistencia | Spring Data JPA + PostgreSQL 16; volumen persistente de Docker |
| Infraestructura local | Docker Compose con PostgreSQL en `localhost:5433` y pgAdmin en `localhost:5050` |
| Seguridad | Spring Security sin sesión, BCrypt y JWT con JJWT 0.12.6 |
| Mapeo | MapStruct 1.5.5.Final + Lombok 1.18.32 |
| Frontend | Angular 20.3, formularios reactivos, señales y SSR/prerender configurado |
| Comunicación | `/api` en desarrollo mediante proxy a `http://localhost:8082/identipat-ia`; `/identipat-ia` en producción |
| Pruebas de API | Colección Postman `postman/identipat-api.collection.json` con 25 escenarios |

El backend concentra el CRUD y el login en `/users`. Solo los usuarios cuyo `userType` es `ADMIN` pueden autenticarse; el JWT identifica al usuario por DOI y las autorizaciones se reconstruyen consultando la base de datos en cada petición autenticada. El frontend sigue siendo una pantalla única de mantenimiento y no implementa el flujo de autenticación.

## Avances identificados

- Se eliminó H2 de las dependencias y se añadió el driver JDBC de PostgreSQL.
- `docker-compose.yml` aprovisiona PostgreSQL 16 y pgAdmin; el volumen `postgres_data` conserva los datos entre reinicios de los contenedores.
- La URL JDBC, el usuario y la contraseña admiten variables de entorno; `.env` está excluido de Git.
- El DOI tiene restricciones de unicidad en la entidad y en el servicio.
- Las contraseñas se codifican con BCrypt y no se incluyen en las respuestas JSON (`WRITE_ONLY`).
- Existen validaciones de DTO, manejo centralizado de excepciones, pruebas unitarias de servicio y mapper, una prueba de contexto y JaCoCo.
- El build Angular de desarrollo finaliza correctamente con la instalación local actual.
- La colección Postman cubre casos exitosos y de error de login, CRUD, duplicidad, validación y acceso sin autenticación.

## Hallazgos y riesgos

### Críticos

1. **Escalamiento de privilegios mediante el registro público.** `POST /users` está permitido sin autenticación y el servicio acepta `userType=ADMIN` junto con una contraseña. Un cliente anónimo puede crear su propia cuenta administradora, autenticarse en `/users/login` y obtener acceso a todos los endpoints protegidos. La creación de administradores debe quedar restringida a un administrador autenticado o a un proceso de aprovisionamiento separado; el alta pública debe forzar el tipo estándar desde el servidor.
2. **Modificación de usuarios sin autenticación.** `PUT /users/**` está permitido para cualquier cliente. Aunque la regla más específica protege `PUT /users/admin/**`, la actualización ordinaria permite cambiar datos personales y `userType`. Debe requerir autenticación y comprobar si el actor puede modificar ese usuario y esos campos.
3. **Exposición pública de datos personales por DOI.** `GET /users/doi/{doi}` no requiere autenticación y devuelve nombre, fecha de nacimiento, género, correo, teléfonos, profesión, tipo y estado. Debe limitarse el contenido de la respuesta, aplicar autorización y evitar que el DOI funcione como mecanismo público de enumeración.

### Altos

1. **El frontend no implementa autenticación.** No hay vista de login, almacenamiento de token, interceptor `Authorization: Bearer ...`, cierre de sesión ni manejo diferenciado de 401/403. Por ello, listar y eliminar usuarios desde la UI falla contra las reglas actuales del backend.
2. **Secretos y credenciales de respaldo predecibles.** `application.properties` incluye valores de desarrollo por defecto para `DB_USER`, `DB_PASSWORD` y `JWT_SECRET`. Si faltan variables de entorno, la aplicación arranca con valores conocidos. Los perfiles no locales deben exigir secretos externos y fallar al arrancar cuando no estén definidos.
3. **Esquema persistente sin migraciones versionadas.** PostgreSQL ya conserva datos, pero `spring.jpa.hibernate.ddl-auto=update` modifica el esquema de forma implícita. Se necesita Flyway o Liquibase y usar `validate` en entornos compartidos y productivos.
4. **Pruebas de integración acopladas a una base local.** `IdentipatIaApplicationTests` usa `@SpringBootTest` y la configuración general apunta a PostgreSQL en el puerto 5433. No existe perfil de test ni Testcontainers, por lo que `mvn test` no es autocontenido.
5. **La colección de API usa un esquema de autenticación incompatible.** Las solicitudes protegidas de `postman/identipat-api.collection.json` están configuradas con HTTP Basic, pero `SecurityConfig` deshabilita HTTP Basic y espera JWT Bearer. Varias descripciones también indican Basic. La colección debe capturar el token del login y reutilizarlo como Bearer.

### Medios

1. **Valores de dominio inconsistentes.** El backend usa `STANDARD`, el frontend ofrece `USER` y el servicio acepta cualquier texto no vacío como `userType`. `gender` y `doiType` también son cadenas libres. Conviene usar enums o validadores compartidos y una única nomenclatura.
2. **Actualización con semántica confusa.** `PUT /users/{id}` exige un `UserDTO` casi completo, ignora DOI, tipo de DOI, contraseña y estado, pero sí cambia `userType`. En la UI, editar un administrador obliga a escribir una contraseña que este endpoint finalmente ignora. Deben separarse actualización de perfil, administración, rol, estado y credenciales.
3. **Consulta sin paginación ni filtros.** `GET /users` devuelve la colección completa, lo que afectará rendimiento y exposición de datos al crecer el volumen.
4. **Mensajes internos expuestos.** El manejador general devuelve `ex.getMessage()` al cliente. Es preferible registrar el detalle internamente y responder un mensaje estable con identificador de correlación.
5. **Dependencia de red durante el build de producción.** `frontend/src/styles.css` importa Google Fonts y Angular intenta incorporarlas durante la optimización. El build de producción falla sin acceso a `fonts.googleapis.com`; conviene alojar las fuentes localmente o configurar el build para que sea reproducible sin red.
6. **Configuración temporal parcialmente inefectiva.** `spring.jackson.time-zone` sí configura Jackson, pero `spring.application.time-zone` no establece de forma estándar la zona de la JVM o de Hibernate. Conviene persistir en UTC o configurar explícitamente `hibernate.jdbc.time_zone` y documentar la política.
7. **Infraestructura incompleta para despliegue.** Compose levanta la base y pgAdmin, pero no existe `Dockerfile` ni servicio para backend/frontend. Tampoco hay separación de configuraciones `dev`, `test` y `prod`.
8. **Documentación operativa limitada.** Ya existe un README breve en la raíz, pero falta un contrato OpenAPI y una guía operativa detallada. `docs/plan-migracion-postgres.md` conserva parte del estado anterior a la migración y debería marcarse como plan ejecutado o actualizarse.

## Calidad y verificaciones realizadas

| Verificación | Resultado | Observación |
| --- | --- | --- |
| Inspección de estructura, configuración y código | Realizada | Incluyó backend, frontend, Docker Compose, colección Postman y reglas de Git. |
| Pruebas backend (`cd backend && mvn test`) | No ejecutadas | Maven no está disponible en `PATH` y el repositorio no incluye Maven Wrapper. |
| Build frontend de desarrollo (`npm run build:dev`) | Correcto | Angular generó el bundle en `frontend/dist/identipat-ia`. |
| Build frontend de producción (`npm run build`) | Bloqueado por entorno | Falló al descargar e incorporar las hojas de estilo de Google Fonts. |
| Pruebas frontend (`npm test -- --watch=false --browsers=ChromeHeadless`) | No ejecutadas completamente | El bundle de pruebas compiló, pero ChromeHeadless terminó por fallos de GPU/permisos antes de ejecutar los 2 casos. |
| Docker | Disponible parcialmente | El cliente Docker 29.7.2 está instalado; no se validó el arranque de los servicios ni la conexión a PostgreSQL. |
| CI/CD | No identificado | `.github` solo contiene la plantilla de pull request; no hay workflows. |

El repositorio declara 15 pruebas backend (12 de `UserService`, 2 de `UserMapper` y 1 de contexto) y 2 pruebas del componente Angular. No hay pruebas de seguridad, controlador, repositorio, integración HTTP ni flujo frontend-backend.

## Recomendaciones priorizadas

1. Cerrar el escalamiento de privilegios: impedir que el alta pública elija `ADMIN` y proteger todas las operaciones de mantenimiento con una matriz explícita de roles y propiedad del recurso.
2. Proteger o rediseñar `GET /users/doi/{doi}` para no exponer datos personales a clientes anónimos.
3. Implementar autenticación completa en Angular y corregir la colección Postman para obtener y enviar JWT Bearer.
4. Crear perfiles `dev`, `test` y `prod`; eliminar valores secretos por defecto fuera de desarrollo y documentar las variables requeridas.
5. Incorporar Flyway/Liquibase, una migración inicial y `ddl-auto=validate`; usar PostgreSQL con Testcontainers para pruebas de integración.
6. Unificar `ADMIN`/`STANDARD` y los demás catálogos de dominio entre backend, frontend y colección; restringirlos con validaciones.
7. Añadir Maven Wrapper y CI para ejecutar pruebas backend, build/pruebas frontend y comprobaciones de seguridad.
8. Hacer reproducible el build de producción alojando las fuentes localmente, y publicar README raíz, OpenAPI y guía de operación.

## Observación de control de versiones

La carpeta `docs/` ya no está excluida por `.gitignore` y sus documentos pueden versionarse normalmente. En cambio, `codex-prompts/` queda reservado para instrucciones locales y está ignorado por completo.
