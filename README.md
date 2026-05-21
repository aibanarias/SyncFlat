# SyncFlat — Gestión integral para pisos compartidos

SyncFlat es una aplicación web para coordinar la convivencia en un piso compartido. Permite gestionar gastos, tareas domésticas, listas de la compra, calendario de eventos y comunicación entre compañeros, todo desde una interfaz común.

Desarrollada como práctica de la asignatura **Ingeniería Web (IW 2024-25)** de la UCM/FDI.

---

## Tecnologías

| Capa | Tecnología |
|------|-----------|
| Backend | Spring Boot 3.4.3 + Spring MVC |
| Persistencia | JPA / Hibernate + H2 (embebida) |
| Seguridad | Spring Security 6 (roles, CSRF, sesiones) |
| Tiempo real | WebSocket + STOMP |
| Vistas | Thymeleaf 3 + Bootstrap 5.3.3 |
| Tests | Karate 1.5.2 (JUnit 5) |
| Build | Maven + Java 21 |

---

## Arquitectura

El proyecto sigue una arquitectura en capas estricta:

```
Controller  →  Service  →  EntityManager (JPA)
```

Los controllers se limitan a resolver el contexto HTTP y delegar en el service correspondiente. Toda la lógica de negocio vive en los services, que acceden a la base de datos mediante JPQL a través de `EntityManager`. 

La seguridad se gestiona con Spring Security 6: autenticación por formulario, roles `USER` y `ADMIN`, protección CSRF y restricción de rutas por rol. La sesión HTTP almacena el usuario activo y el piso al que pertenece, de modo que todos los módulos pueden resolver el contexto sin consultas adicionales.

El sistema de mensajería en tiempo real usa WebSocket con protocolo STOMP. Las alertas del piso se difunden por `/topic` y los mensajes directos entre usuarios van por `/queue`. La autenticación WebSocket está integrada con Spring Security.

---

## Funcionalidades principales

### Panel principal (Home)

Dashboard con el estado actual del piso: alertas no leídas, próximos eventos, tareas pendientes asignadas al usuario y gastos recientes. Las alertas nuevas aparecen en tiempo real sin recargar la página (WebSocket). Desde aquí también se gestiona el piso: ver miembros, asignar habitaciones y compartir el código de invitación.

### Gestión del piso y membresías

Cualquier usuario puede crear un piso nuevo o unirse a uno existente usando un código de invitación de seis caracteres. Cada piso tiene un administrador que puede configurar el número de habitaciones, promover a otros miembros a administrador y gestionar el acceso. Al abandonar el piso, si el que se va es el único administrador, el rol se transfiere automáticamente al miembro más antiguo.

### Gastos compartidos

Los miembros pueden registrar gastos indicando el importe y a quién afectan. El reparto es automático y proporcional. La vista de gastos muestra tres secciones: el balance neto entre pares (compensando deudas cruzadas), las operaciones pendientes del usuario y el historial completo con detalle de participantes desplegable. Cada participante puede marcar su parte como pagada.

### Listas de la compra

Gestión de listas de la compra del piso. Los miembros añaden productos al catálogo del piso y los incluyen en listas activas con cantidad y responsable. Cada ítem se puede marcar como comprado mediante AJAX. Al registrar una compra se cierra la lista y, opcionalmente, se genera un gasto compartido asociado.

### Tareas domésticas

Creación de tareas puntuales o recurrentes (diarias, semanales, quincenales o mensuales) con asignación a un miembro concreto. El flujo de una tarea sigue tres estados: pendiente → completada (la marca el asignado) → validada (la confirma otro miembro). Todo el ciclo funciona mediante AJAX sin recargas.

### Calendario y disponibilidad

Calendario visual de eventos del piso (FullCalendar). Los eventos pasan por un flujo de aprobación: propuesto → aprobado/rechazado por el administrador. Cada usuario puede indicar su asistencia con un ciclo de tres estados. Además, cada miembro puede registrar sus bloques horarios personales (trabajo, clases, entrenamiento u otro), y el sistema detecta automáticamente conflictos entre un evento y los bloques de los asistentes.

### Notificaciones en tiempo real

Cuando se produce un evento relevante en el piso (nuevo gasto, tarea pendiente, alerta urgente…) todos los miembros conectados reciben una notificación en forma de toast sin necesidad de recargar. El icono de campana en la barra de navegación acumula el contador de alertas no leídas y se actualiza en directo.


### Perfil de usuario

Cada usuario puede editar su nombre, apellido y contraseña, y subir una foto de perfil. Los administradores del sistema pueden modificar cualquier perfil y habilitar o deshabilitar cuentas desde el panel de administración.

---

## Usuarios de prueba

La base de datos se inicializa al arrancar con cinco usuarios. Todos tienen la contraseña `aa`.

| Usuario | Contraseña | Roles | Piso |
|---------|-----------|-------|------|
| `a` | `aa` | ADMIN, USER | Piso Moncloa (administrador) |
| `b` | `aa` | USER | Piso Moncloa |
| `c` | `aa` | USER | Sin piso asignado |
| `d` | `aa` | USER | Piso Retiro (administrador) |
| `e` | `aa` | USER | Piso Moncloa |

El usuario `a` tiene rol ADMIN a nivel de sistema (acceso a `/admin`). Los roles ADMIN/MIEMBRO dentro de un piso son independientes del rol de sistema.

En modo debug, la barra de navegación muestra botones de login rápido para `a` y `b`.

---

## Cómo ejecutar

**Requisitos:** JDK 21 y Maven 3.8+.

```bash
# Desde la raíz del proyecto
mvn spring-boot:run
```

La aplicación arranca en **http://localhost:8080**.

Se usa una base de datos H2 en memoria. Los datos se crean al arrancar desde `src/main/resources/import.sql` y se pierden al detener la aplicación.

La consola H2 está disponible en **http://localhost:8080/h2** (solo en modo debug).

No existe formulario de registro público. Los usuarios se crean mediante el seed inicial o desde el panel de administración (`/admin`).

---

## Estructura del repositorio

`src/` y `pom.xml` están en la raíz del proyecto.

```
pom.xml
src/
├── main/
│   ├── java/es/ucm/fdi/iw/
│   │   ├── IwApplication.java
│   │   ├── SecurityConfig.java
│   │   ├── WebSocketConfig.java
│   │   ├── WebSocketSecurityConfig.java
│   │   ├── LoginSuccessHandler.java
│   │   ├── IwUserDetailsService.java
│   │   ├── controller/
│   │   │   ├── BaseController.java
│   │   │   ├── RootController.java
│   │   │   ├── HomeController.java
│   │   │   ├── PisoController.java
│   │   │   ├── GastoController.java
│   │   │   ├── TareaController.java
│   │   │   ├── CompraController.java
│   │   │   ├── CalendarioController.java
│   │   │   ├── AlertaController.java
│   │   │   ├── UserController.java
│   │   │   ├── AdminController.java
│   │   │   ├── ApiController.java
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── service/
│   │   │   ├── PisoService.java
│   │   │   ├── GastoService.java
│   │   │   ├── TareaService.java
│   │   │   ├── CompraService.java
│   │   │   ├── CalendarioService.java
│   │   │   └── AlertaService.java
│   │   ├── dto/                    # Form DTOs y ApiResponse
│   │   └── model/                  # 17 entidades JPA + 6 enums
│   └── resources/
│       ├── application.properties
│       ├── application-container.properties
│       ├── import.sql
│       └── templates/
│           ├── fragments/          # head.html, nav.html, footer.html, alerts.html
│           ├── index.html, login.html, autores.html, error.html
│           ├── home.html, gastos.html, compra.html, compra-gestion.html
│           ├── tareas.html, calendario.html, user.html, admin.html
│           └── static/css, js, img/
└── test/
    └── java/es/ucm/fdi/iw/
        ├── PruebaTest.java         # Runner JUnit 5 de Karate
        └── *.feature               # 16 ficheros de escenarios Karate
```

---

## Modelo de datos

El esquema se genera automáticamente por Hibernate a partir de las entidades (`ddl-auto=create-drop`). La descripción detallada del modelo y las decisiones de diseño está en [`datadoc.md`](datadoc.md). El diagrama ER está en `bd.png`.

### Entidades (17) y enumerados (6)

| Entidad | Descripción |
|---------|------------|
| `User` | Usuario del sistema. Roles: `USER`, `ADMIN` |
| `Piso` | Piso compartido (entidad central) |
| `MiembroPiso` | Relación usuario-piso con rol (`ADMIN`, `MIEMBRO`) |
| `Gasto` | Gasto registrado por un pagador. Estado: `PENDIENTE`, `LIQUIDADO` |
| `ParticipanteGasto` | Parte que corresponde a cada miembro en un gasto |
| `Tarea` | Tarea doméstica. Tipo: `PUNTUAL`, `RECURRENTE` |
| `AsignacionTarea` | Asignación de una tarea a un usuario concreto |
| `ListaCompra` | Lista de la compra del piso |
| `ItemListaCompra` | Ítem dentro de una lista de la compra |
| `Producto` | Producto del catálogo del piso |
| `Compra` | Compra realizada sobre una lista |
| `Evento` | Evento del calendario del piso |
| `AsistenciaEvento` | Respuesta de un miembro a un evento (`PENDIENTE`, `CONFIRMADO`, `RECHAZADO`) |
| `BloqueHorario` | Franja horaria personal. Tipo: `TRABAJO`, `CLASES`, `ENTRENAMIENTO`, `OTRO` |
| `Alerta` | Aviso del piso. Tipo: `INFO`, `URGENTE`, `RECORDATORIO` |
| `Message` | Mensaje directo entre usuarios |
| `Topic` | Canal de mensajería grupal |

---

## Recursos externos utilizados

Los siguientes recursos son externos a la plantilla de la asignatura:

| Recurso | Versión | Uso |
|---------|---------|-----|
| [FullCalendar](https://fullcalendar.io/) | 6.1.15 | Calendario visual interactivo en el módulo de calendario. Cargado desde CDN. |
| [Bootstrap Icons](https://icons.getbootstrap.com/) | 1.11.3 | Iconografía en botones, badges y cabeceras de sección. Cargado desde CDN. |
| WhatsApp Share Link | — | Fallback mediante enlace externo `https://wa.me/?text=...` para compartir el código de invitación, sin integración con la API oficial de WhatsApp. |El resto de dependencias de frontend (Bootstrap 5.3.3, STOMP.js, iw.js) proceden de la plantilla base de la asignatura. Las dependencias de backend se gestionan desde `pom.xml` con Maven.

---

## Uso de inteligencia artificial

Se ha utilizado **Claude Sonnet 4.6** (Anthropic) como herramienta de apoyo en varias fases del desarrollo:

- **Documentación:** generación de Javadoc, ayuda para generación de partes del README.
- **Tests:** ayuda para completar la suite Karate, especialmente en los escenarios de seguridad y flujos más complejos (sucesión de administrador, recurrencia de tareas, acceso cross-piso).
- **Repetición de patrones:** generación de formularios con estructura similar a los ya existentes.


---

## Tests (Karate)

Los tests se ejecutan con:

```bash
mvn test
```

`PruebaTest` arranca el servidor embebido en un puerto aleatorio y pasa el puerto a Karate mediante una propiedad de sistema. No es necesario levantar el servidor manualmente.

**Resultado:** 64 escenarios en 16 ficheros `.feature`, todos en verde.

| Feature | Qué cubre |
|---------|-----------|
| `auth.feature` | Autenticación, acceso sin sesión, redirección a login |
| `prueba.feature` | Login, CSRF, smoke tests de vistas, creación de gasto y tarea |
| `piso.feature` | Crear piso, unirse con código, gestión de membresías |
| `habitaciones.feature` | Asignación de habitaciones a miembros |
| `habitaciones_admin.feature` | Configuración de habitaciones por el administrador |
| `gastos.feature` | Crear gasto, pagar participación, validación de errores |
| `compra.feature` | Añadir ítem, toggle comprado, flujo de compra |
| `registrar_compra.feature` | Registro de compra y generación de gasto asociado |
| `tareas.feature` | Crear tarea, completar asignación |
| `validacion_tareas.feature` | Flujo de validación de tareas completadas |
| `seguridad_tareas.feature` | Control de acceso entre usuarios de distintos pisos |
| `vr_recurrencia.feature` | Validación de tareas recurrentes |
| `calendario.feature` | Bloques horarios, conflictos AJAX, asistencia a eventos |
| `alertas.feature` | Creación y lectura de alertas del piso |
| `admin.feature` | Panel ADMIN, toggle habilitado/deshabilitado |
| `z_sucesion_admin.feature` | Transferencia de administrador al abandonar el piso |

---

## Configuración

### Desarrollo (`application.properties`)

- H2 en memoria (`jdbc:h2:mem:iwdb`), se recrea al arrancar
- `ddl-auto=create-drop` + `import.sql` para datos de prueba
- Consola H2 en `/h2` y caché de plantillas desactivada
- `es.ucm.fdi.debug=true` — activa botones de login rápido en la navbar

### Producción (`application-container.properties`, perfil `container`)

- H2 persistente en fichero (`jdbc:h2:file:./iwdb`)
- `ddl-auto=create`: recrea las tablas en cada arranque y ejecuta `import.sql`; los datos se persisten en disco hasta el siguiente inicio
- Caché activada, consola H2 desactivada, puerto 80, debug desactivado
- Ficheros de entrega: `iwdb.mv.db` (raíz del proyecto) e `iwdata/` (comprimido como `iwdata.zip`)

### Despliegue

```bash
python deploy.py
```

El script empaqueta el JAR, sube los ficheros vía SSH y reinicia el contenedor Docker en la VM de la FDI. Requiere `credentials.json` (no se sube al repositorio; usar `credentials.json.template` como plantilla).

---

