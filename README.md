# SyncFlat — Gestión integral para pisos compartidos

Aplicación web para coordinar la convivencia en un piso compartido: gastos, tareas, compras, calendario y mensajería. Desarrollada como práctica de la asignatura **Ingeniería Web (IW 2024-25)** de la UCM/FDI.

## Tecnologías

| Capa | Tecnología |
|------|-----------|
| Backend | Spring Boot 3.4.3 + Spring MVC |
| Persistencia | JPA / Hibernate + H2 (embebida) |
| Seguridad | Spring Security 6 (roles, CSRF, sesiones) |
| Tiempo real | WebSocket + STOMP |
| Vistas | Thymeleaf 3 + Bootstrap 5.3.3 |
| Tests | Karate 1.4.1 (JUnit 5) |
| Build | Maven + Java 21 |

## Cómo ejecutar

**Requisitos:** JDK 21 y Maven 3.8+.

```bash
# Desde la raíz del proyecto
mvn spring-boot:run
```

La aplicación arranca en **http://localhost:8080**.

En modo desarrollo (por defecto) se usa una base de datos H2 en memoria. Los datos se crean al arrancar desde `src/main/resources/import.sql` y se pierden al parar la aplicación.

La consola H2 está disponible en **http://localhost:8080/h2** (solo en modo debug).

### Usuarios de prueba

| Usuario | Contraseña | Rol |
|---------|-----------|-----|
| `a` | `aa` | ADMIN + USER |
| `b` | `aa` | USER |

Ambos pertenecen al **Piso Moncloa** (`id=1`), que tiene datos de prueba cargados al arrancar.

En modo debug, la barra de navegación muestra botones de login rápido para `a` y `b`.

## Base de datos y modelo JPA

El esquema se genera automáticamente por Hibernate a partir de las entidades (`ddl-auto=create-drop`). Los datos iniciales se cargan desde `import.sql`.

### Entidades (15) y enumerados (5)

| Entidad | Descripción |
|---------|------------|
| `User` | Usuario del sistema. Roles: `USER`, `ADMIN` |
| `Piso` | Piso compartido (entidad central) |
| `MiembroPiso` | Relación usuario-piso con rol (`PROPIETARIO`, `INQUILINO`) |
| `Gasto` | Gasto registrado por un pagador. Estado: `PENDIENTE`, `LIQUIDADO` |
| `ParticipanteGasto` | Parte que corresponde a cada miembro en un gasto |
| `Liquidacion` | Cierre de período de gastos del piso |
| `Tarea` | Tarea doméstica. Tipo: `PUNTUAL`, `RECURRENTE` |
| `AsignacionTarea` | Asignación de una tarea a un usuario concreto |
| `ListaCompra` | Lista de la compra del piso |
| `ItemListaCompra` | Ítem dentro de una lista de la compra |
| `Producto` | Producto del catálogo del piso |
| `Compra` | Compra realizada sobre una lista |
| `Evento` | Evento del calendario del piso |
| `AsistenciaEvento` | Respuesta de un usuario a un evento (`PENDIENTE`, `CONFIRMADO`, `RECHAZADO`) |
| `Ausencia` | Período de ausencia de un miembro |
| `Alerta` | Aviso del piso. Tipo: `INFO`, `URGENTE`, `RECORDATORIO` |
| `Message` | Mensaje entre usuarios (plantilla) |
| `Topic` | Canal de mensajería (plantilla) |

El diagrama ER de referencia está en `bd.png`. La descripción detallada del modelo, decisiones de diseño y generación de IDs está en [`datadoc.md`](datadoc.md).

## Estructura del proyecto

```
src/
├── main/
│   ├── java/es/ucm/fdi/iw/
│   │   ├── IwApplication.java           # Punto de entrada
│   │   ├── SecurityConfig.java          # Seguridad HTTP y roles
│   │   ├── WebSocketConfig.java         # Broker STOMP
│   │   ├── LoginSuccessHandler.java     # Redirect post-login
│   │   ├── IwUserDetailsService.java    # Carga usuarios desde JPA
│   │   ├── AppConfig.java               # Beans auxiliares
│   │   ├── StartupConfig.java           # Lee es.ucm.fdi.debug
│   │   ├── controller/
│   │   │   ├── RootController.java      # Módulos principales (gastos, tareas, compra, calendario, home)
│   │   │   ├── UserController.java      # Perfil, mensajería, fotos
│   │   │   ├── AdminController.java     # Panel de administración
│   │   │   └── ApiController.java       # API REST pública
│   │   └── model/                       # 15 entidades JPA + 5 enums
│   └── resources/
│       ├── application.properties       # Configuración desarrollo (H2 en memoria)
│       ├── application-container.properties  # Configuración producción (H2 fichero)
│       ├── import.sql                   # Datos iniciales de prueba
│       └── templates/
│           ├── fragments/               # head.html, nav.html, footer.html
│           ├── index.html, login.html, autores.html, error.html
│           ├── home.html, gastos.html, compra.html, tareas.html, calendario.html
│           ├── user.html, admin.html
│           └── static/css, js, img/     # Bootstrap 5.3.3, iw.js, stomp.js
└── test/
    └── java/es/ucm/fdi/iw/
        ├── PruebaTest.java              # Runner JUnit 5 de Karate
        └── prueba.feature               # 10 escenarios Karate
```

## Rutas principales

Todas las rutas de `/modulos/**` requieren autenticación. Un usuario sin sesión es redirigido automáticamente a `/login`.

### Rutas GET (lectura / render de vista)

| Ruta | Vista | Acceso | Descripción |
|------|-------|--------|-------------|
| `GET /` | index.html | Público | Landing page |
| `GET /login` | login.html | Público | Formulario de login |
| `GET /autores` | autores.html | Público | Información del autor |
| `GET /modulos/home` | home.html | Autenticado | Dashboard: eventos, tareas, gastos y alertas del piso |
| `GET /modulos/gastos` | gastos.html | Autenticado | Listado de gastos y participantes |
| `GET /modulos/compra` | compra.html | Autenticado | Listas de la compra e ítems |
| `GET /modulos/tareas` | tareas.html | Autenticado | Tareas y asignaciones del piso |
| `GET /modulos/calendario` | calendario.html | Autenticado | Eventos y asistencias |
| `GET /user/{id}` | user.html | ROLE_USER | Perfil de usuario |
| `GET /admin/` | admin.html | ROLE_ADMIN | Panel de administración |

### Rutas POST (escritura / modificación)

| Ruta | Acción | Acceso |
|------|--------|--------|
| `POST /login` | Autenticación (gestionado por Spring Security) | Público |
| `POST /logout` | Cierre de sesión | Autenticado |
| `POST /modulos/gastos` | Crear gasto y repartir entre miembros | Autenticado |
| `POST /modulos/compra/item` | Añadir ítem a lista de la compra | Autenticado |
| `POST /modulos/compra/item/{id}/toggle` | Marcar/desmarcar ítem como comprado (AJAX, devuelve JSON) | Autenticado |
| `POST /modulos/tareas` | Crear tarea con asignación opcional | Autenticado |
| `POST /modulos/tareas/{id}/completar` | Completar o reabrir asignación de tarea (AJAX, devuelve JSON) | Autenticado |
| `POST /modulos/calendario` | Crear evento | Autenticado |
| `POST /modulos/calendario/asistencia/{eventoId}` | Cambiar mi asistencia a un evento (AJAX, devuelve JSON) | Autenticado |
| `POST /user/{id}` | Editar perfil de usuario | ROLE_USER |
| `POST /user/{id}/pic` | Subir foto de perfil | ROLE_USER |
| `POST /user/{id}/msg` | Enviar mensaje a otro usuario (WebSocket) | ROLE_USER |
| `POST /admin/toggle/{id}` | Habilitar/deshabilitar usuario (AJAX) | ROLE_ADMIN |

## Estado de implementación por módulo

| Módulo | Ruta | Estado | Detalle |
|--------|------|--------|---------|
| **Landing** | `GET /` | ✅ Completo | Presentación y enlaces a módulos |
| **Login** | `GET /login` | ✅ Completo | Formulario con CSRF; botones de login rápido en modo debug |
| **Home** | `GET /modulos/home` | ✅ Completo | Dashboard con datos reales: próximos 5 eventos, 5 tareas pendientes, 5 gastos recientes y alertas no leídas |
| **Gastos** | `GET+POST /modulos/gastos` | ✅ Completo | Crear gasto, reparto automático entre miembros, tabla de participantes |
| **Compra** | `GET+POST /modulos/compra` | ✅ Completo | Añadir ítems, toggle comprado/pendiente vía AJAX |
| **Tareas** | `GET+POST /modulos/tareas` | ✅ Completo | Crear tarea, asignar a miembro, completar/reabrir vía AJAX |
| **Calendario** | `GET+POST /modulos/calendario` | ✅ Completo | Crear eventos, confirmar/rechazar asistencia vía AJAX |
| **Usuario** | `GET+POST /user/{id}` | ✅ Completo | Perfil, foto, mensajería en tiempo real (WebSocket/STOMP) |
| **Admin** | `GET+POST /admin/` | ✅ Completo | Listar usuarios, habilitar/deshabilitar |
| **Liquidaciones** | — | ⏳ Pendiente | Entidad y datos en BD; sin vista ni lógica de negocio |
| **Ausencias** | — | ⏳ Pendiente | Entidad y datos en BD; sin vista |

## Tests (Karate)

Los tests están en `src/test/java/es/ucm/fdi/iw/prueba.feature` y se ejecutan con:

```bash
mvn test
```

Los 10 escenarios cubren:

| Nº | Escenario | Tipo |
|----|-----------|------|
| 1 | Abrir login y capturar token CSRF | Seguridad |
| 2 | Login correcto y acceso al home | Autenticación |
| 3 | Ver dashboard con datos del piso | Smoke test |
| 4 | Ver listado de gastos | Smoke test |
| 5 | Ver lista de la compra | Smoke test |
| 6 | Ver tareas del piso | Smoke test |
| 7 | Admin accede a panel de administración | Control de acceso |
| 8 | Usuario sin rol ADMIN recibe 403 en `/admin/` | Control de acceso |
| 9 | Crear gasto y verificar que aparece en el listado | **Negocio + persistencia** |
| 10 | Crear tarea y verificar que aparece en el listado | **Negocio + persistencia** |

Los escenarios 9 y 10 verifican el ciclo completo POST → H2 → GET.

## Configuración

### Desarrollo (`application.properties`)

- H2 en memoria (`jdbc:h2:mem:iwdb`), se recrea al arrancar
- `ddl-auto=create-drop` + `import.sql` para datos de prueba
- Consola H2 en `/h2`, caché desactivada, stacktrace en errores
- `es.ucm.fdi.debug=true` — activa botones de debug en la navbar

### Producción (`application-container.properties`, perfil `container`)

- H2 persistente en fichero (`jdbc:h2:file:./iwdb`)
- `ddl-auto=validate`, caché activada, consola H2 desactivada
- Puerto 80, debug desactivado

### Despliegue

```bash
python deploy.py
```

El script empaqueta el JAR, sube los ficheros vía SSH y reinicia el contenedor Docker en la VM de la FDI. Requiere `credentials.json` (no se sube al repositorio; usar `credentials.json.template` como base).
