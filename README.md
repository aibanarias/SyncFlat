# SyncFlat — Gestión integral para pisos compartidos

## ¿Qué es SyncFlat?

SyncFlat es una aplicación web orientada a personas que **comparten piso** y necesitan coordinar de forma clara y justa el dinero, la organización doméstica y el tiempo. El sistema se estructura en módulos independientes pero conectados entre sí.

## Funcionalidades actuales (entrega)

- **Página de inicio** (`/`): presentación del proyecto y enlaces a los módulos.
- **Módulo de Gastos** (`/modulos/gastos`): descripción de la funcionalidad de control de gastos compartidos.
- **Módulo de Compra** (`/modulos/compra`): organización de compras y reparto flexible.
- **Módulo Home** (`/modulos/home`): panel de control centralizado del piso.
- **Módulo de Tareas** (`/modulos/tareas`): gestión de tareas domésticas con **demo JavaScript interactiva** (botón que añade ítems a una lista sin backend).
- **Módulo de Calendario** (`/modulos/calendario`): planificación y disponibilidad compartida.
- **Autores** (`/autores`): información sobre el autor del proyecto.
- **Login y seguridad**: autenticación con formulario; botones de debug para login rápido (usuarios A y B).
- **Administración** (`/admin/`): visible solo para usuarios con rol ADMIN.

## Cómo ejecutar

### Requisitos
- **Java 21** (JDK)
- **Maven 3.8+** (o usar el wrapper `mvnw` si está disponible)

### Comandos

```bash
# Desde la raíz del repositorio
mvn spring-boot:run
```

La aplicación se inicia en **http://localhost:8080**.

## Estructura del proyecto

```
├── pom.xml                          # Configuración Maven
├── README.md                        # Este fichero
├── src/
│   ├── main/
│   │   ├── java/es/ucm/fdi/iw/
│   │   │   ├── controller/
│   │   │   │   ├── RootController.java      # Endpoints principales
│   │   │   │   ├── AdminController.java     # Panel de administración
│   │   │   │   ├── UserController.java      # Gestión de usuarios
│   │   │   │   └── ApiController.java       # API REST
│   │   │   ├── model/                       # Entidades JPA
│   │   │   ├── SecurityConfig.java          # Configuración de seguridad
│   │   │   ├── LoginSuccessHandler.java     # Handler post-login
│   │   │   ├── StartupConfig.java           # Configuración inicial
│   │   │   └── IwApplication.java           # Punto de entrada
│   │   └── resources/
│   │       ├── application.properties       # Configuración de la app
│   │       ├── templates/
│   │       │   ├── fragments/               # Fragmentos Thymeleaf (head, nav, footer)
│   │       │   ├── index.html               # Página principal
│   │       │   ├── login.html               # Formulario de login
│   │       │   ├── gastos.html, compra.html, home.html, tareas.html, calendario.html
│   │       │   ├── autores.html             # Info del autor
│   │       │   └── admin.html, user.html, error.html
│   │       └── static/
│   │           ├── css/                     # Estilos (Bootstrap 5.3.3 + custom.css)
│   │           ├── js/                      # Scripts (Bootstrap, WebSocket, utilidades)
│   │           └── img/                     # Imágenes (logo, favicon, fotos)
│   └── test/                                # Tests (JUnit, Karate)
```

## Login y roles (debug)

La aplicación incluye **botones de login rápido** (visibles solo en modo debug):

| Botón | Usuario | Contraseña | Roles |
|-------|---------|------------|-------|
| **a** | `a` | `aa` | USER, ADMIN |
| **b** | `b` | `aa` | USER |

Estos botones aparecen en la esquina superior derecha de la barra de navegación cuando no hay sesión activa.

Tras hacer login, la navbar muestra los enlaces a todos los módulos. El enlace **Administrar** solo es visible para usuarios con rol ADMIN.

## Rutas y vistas

| Ruta | Vista | Acceso |
|------|-------|--------|
| `GET /` | index.html | Público |
| `GET /login` | login.html | Público |
| `GET /modulos/gastos` | gastos.html | Requiere login (USER) |
| `GET /modulos/compra` | compra.html | Requiere login (USER) |
| `GET /modulos/home` | home.html | Requiere login (USER) |
| `GET /modulos/tareas` | tareas.html | Requiere login (USER) |
| `GET /modulos/calendario` | calendario.html | Requiere login (USER) |
| `GET /autores` | autores.html | Requiere login (USER) |
| `GET /admin/` | admin.html | Requiere ADMIN |
| `GET /user/{id}` | user.html | Requiere login (USER) |

## Notas de escalabilidad

### Arquitectura propuesta para crecer

```
controller/     → Recibe peticiones HTTP, delega en servicios
service/        → Lógica de negocio (a crear cuando se implementen funcionalidades reales)
repository/     → Acceso a datos JPA (Spring Data)
model/          → Entidades de dominio
dto/            → Objetos de transferencia (a crear)
```

### Módulos futuros (roadmap)

1. **Persistencia de gastos**: entidad `Gasto` con JPA, CRUD completo, reparto automático.
2. **Lista de la compra**: entidad `ItemCompra`, listas compartidas en tiempo real (WebSocket).
3. **Tareas con rotación**: entidad `Tarea`, asignación automática por turnos, historial.
4. **Calendario compartido**: entidad `Evento`, vistas por semana/mes, detección de conflictos.
5. **Notificaciones**: sistema de avisos push vía WebSocket (ya preparado en la plantilla).

### Consideraciones de seguridad

- Validación de entrada en todos los formularios (Bean Validation / `@Valid`).
- CSRF habilitado (excepto API REST).
- Autorización por roles (`@Secured`, `@PreAuthorize`).
- Sanitización de datos para evitar XSS.

### Separación de responsabilidades

- **Templates**: solo presentación, usando fragmentos reutilizables (`head`, `nav`, `footer`).
- **Controllers**: delegar siempre en servicios, no contener lógica de negocio.
- **Servicios**: encapsular lógica, transacciones y validaciones.
- **Repositorios**: solo acceso a datos, sin lógica.