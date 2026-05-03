# DataDoc — Modelo de datos de SyncFlat

## 1. Visión general

SyncFlat gestiona la convivencia en un piso compartido. El modelo gira en torno a la entidad **`Piso`**, que actúa como raíz de agregado: prácticamente todos los datos de la aplicación pertenecen a un piso concreto y se consultan siempre filtrando por él.

El acceso al piso del usuario se resuelve en cada petición a partir de la sesión HTTP (ver `RootController.resolverPiso()`), sin almacenar estado entre peticiones.

```
User ──┬── MiembroPiso ──> Piso
       │                    │
       │         ┌──────────┼──────────┬──────────┬──────────┬──────────┐
       │       Gasto      Tarea     Evento     Lista     Alerta   Liquidacion
       │         │          │          │        Compra
       │   Participante  Asignacion Asistencia   │
       │     Gasto        Tarea      Evento    Item
       │                                      Lista
       ├── Ausencia                           Compra
       └── BloqueHorario  (franjas horarias personales)
```

---

## 2. Entidad central: `Piso`

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | `long` | Clave primaria (IDENTITY) |
| `nombre` | `String` | Nombre descriptivo del piso |
| `direccion` | `String` | Dirección postal |
| `fechaCreacion` | `LocalDate` | Fecha en que se registró el piso |

Todas las entidades del dominio tienen una FK a `Piso`. Esto permite que varios pisos coexistan en la misma base de datos sin interferir, y facilita que las consultas filtren siempre por `piso_id`.

---

## 3. Entidades por módulo

### Módulo de usuarios y pertenencia

#### `User` — tabla `IWUser`

> El nombre de tabla es `IWUser` porque `USER` es una palabra reservada en H2.

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | `long` | Clave primaria (**SEQUENCE** `gen`) |
| `username` | `String` | Nombre de usuario (único) |
| `password` | `String` | Hash BCrypt con prefijo `{bcrypt}` |
| `firstName` / `lastName` | `String` | Nombre y apellido |
| `roles` | `String` | CSV de roles: `"USER"`, `"ADMIN,USER"` |
| `enabled` | `boolean` | Si la cuenta está activa |

Relaciones: ninguna FK saliente; es referenciada por casi todas las entidades.

#### `MiembroPiso`

Asociación entre un `User` y un `Piso`. Permite que un usuario pertenezca a un piso con un rol concreto y registra el historial de entrada y salida.

| Campo | Tipo | Descripción |
|---|---|---|
| `piso` | `Piso` | FK al piso |
| `usuario` | `User` | FK al usuario |
| `rolEnPiso` | `RolPiso` | `PROPIETARIO` o `INQUILINO` |
| `fechaIngreso` | `LocalDate` | Fecha de incorporación al piso |
| `fechaSalida` | `LocalDate` | `null` = miembro activo |

#### `Ausencia`

Período de ausencia de un usuario del piso. Sin relación con `Piso`; pertenece directamente al usuario.

| Campo | Tipo | Descripción |
|---|---|---|
| `usuario` | `User` | FK al usuario ausente |
| `fechaInicio` / `fechaFin` | `LocalDate` | Rango de la ausencia |
| `motivo` | `String` | Descripción libre |

---

### Módulo de gastos

#### `Gasto`

Gasto compartido registrado por un miembro del piso.

| Campo | Tipo | Descripción |
|---|---|---|
| `concepto` | `String` | Descripción del gasto |
| `importe` | `BigDecimal` | Importe total en euros |
| `fecha` | `LocalDate` | Fecha del gasto |
| `pagador` | `User` | Quien adelantó el dinero |
| `piso` | `Piso` | FK al piso |
| `estado` | `EstadoGasto` | `PENDIENTE` o `LIQUIDADO` |

#### `ParticipanteGasto`

Registra la parte que corresponde a cada miembro de un gasto. El pagador original queda marcado como `pagado = true` en el momento de crear el gasto.

| Campo | Tipo | Descripción |
|---|---|---|
| `gasto` | `Gasto` | FK al gasto |
| `usuario` | `User` | FK al participante |
| `importeAsignado` | `BigDecimal` | Cuota asignada (reparto a partes iguales) |
| `pagado` | `boolean` | Si el participante ha abonado su parte |

> **Por qué existe:** `Gasto` solo sabe quién pagó y cuánto. `ParticipanteGasto` modela el reparto, permitiendo que cada miembro lleve su propio estado de liquidación de forma independiente. Cuando todos los participantes tienen `pagado = true`, el gasto pasa a `LIQUIDADO`.

#### `Liquidacion`

Cierre periódico de cuentas del piso. Registra el período que abarca sin entrar en el detalle por gasto (ese detalle está en `ParticipanteGasto`).

| Campo | Tipo | Descripción |
|---|---|---|
| `piso` | `Piso` | FK al piso |
| `fecha` | `LocalDate` | Fecha de la liquidación |
| `periodoInicio` / `periodoFin` | `LocalDate` | Rango temporal cerrado |

---

### Módulo de tareas

#### `Tarea`

Tarea doméstica definida en el contexto de un piso. Puede ser puntual o periódica.

| Campo | Tipo | Descripción |
|---|---|---|
| `nombre` | `String` | Nombre de la tarea |
| `descripcion` | `String` | Descripción libre |
| `tipo` | `TipoTarea` | `PUNTUAL` o `RECURRENTE` |
| `frecuencia` | `String` | Texto libre: `"SEMANAL"`, `"MENSUAL"`… |
| `fechaLimite` | `LocalDate` | Fecha máxima para completarla |
| `piso` | `Piso` | FK al piso |

#### `AsignacionTarea`

Asignación de una tarea a un usuario concreto. Una misma tarea puede tener varias asignaciones a lo largo del tiempo (por ejemplo en tareas recurrentes).

| Campo | Tipo | Descripción |
|---|---|---|
| `tarea` | `Tarea` | FK a la tarea |
| `usuario` | `User` | FK al responsable |
| `fechaAsignacion` | `LocalDate` | Cuándo se asignó |
| `fechaCompletada` | `LocalDate` | `null` = pendiente |
| `validada` | `boolean` | Si otro miembro ha confirmado la realización |
| `validador` | `User` | FK al validador (puede ser `null`) |

> **Por qué existe:** Separar `Tarea` de su asignación permite reusar la definición de una tarea (nombre, tipo, fechaLimite) en múltiples ciclos o asignarla a distintas personas sin duplicar datos. El estado pendiente/completado vive en `AsignacionTarea`, no en `Tarea`.

---

### Módulo de calendario

#### `Evento`

Evento del calendario compartido del piso.

| Campo | Tipo | Descripción |
|---|---|---|
| `titulo` | `String` | Título del evento |
| `descripcion` | `String` | Descripción libre |
| `fechaInicio` / `fechaFin` | `LocalDateTime` | Rango del evento (con hora) |
| `creador` | `User` | FK al usuario que lo creó |
| `piso` | `Piso` | FK al piso |

#### `AsistenciaEvento`

Respuesta de un miembro a un evento. Se crea al primer clic del usuario y puede cambiarse cíclicamente.

| Campo | Tipo | Descripción |
|---|---|---|
| `evento` | `Evento` | FK al evento |
| `usuario` | `User` | FK al miembro |
| `estado` | `EstadoAsistencia` | `PENDIENTE` → `CONFIRMADO` → `RECHAZADO` → `PENDIENTE` |

> **Por qué existe:** Si el estado de asistencia estuviera en `Evento`, solo podría almacenarse una respuesta por evento. `AsistenciaEvento` permite que cada miembro tenga su propia respuesta, independiente de la de los demás.

#### `BloqueHorario` — tabla `bloque_horario`

Franja horaria personal de un usuario. Se usa para detectar conflictos entre los compromisos individuales y los eventos del piso.

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | `long` | Clave primaria (IDENTITY) |
| `usuario` | `User` | FK al propietario del bloque |
| `tipo` | `TipoBloque` | Categoría del bloque: `TRABAJO`, `CLASES`, `ENTRENAMIENTO`, `OTRO` |
| `descripcion` | `String` | Descripción libre (opcional) |
| `inicio` | `LocalDateTime` | Inicio del bloque (con hora) |
| `fin` | `LocalDateTime` | Fin del bloque (con hora); debe ser posterior a `inicio` |

> **Por qué existe:** Un miembro puede tener compromisos personales que no son eventos del piso pero que sí afectan a su disponibilidad. `BloqueHorario` permite registrar esas franjas para que el sistema pueda advertir (de forma informativa) cuando un evento nuevo solapa con ellas. La detección se basa en la condición de solapamiento: `bloque.inicio < evento.fin AND bloque.fin > evento.inicio`.

---

### Módulo de lista de la compra

#### `ListaCompra`

Lista de la compra de un piso para un período o propósito concreto.

| Campo | Tipo | Descripción |
|---|---|---|
| `nombre` | `String` | Nombre de la lista |
| `fechaCreacion` | `LocalDate` | Cuándo se creó |
| `completada` | `boolean` | Si la compra ya se realizó |
| `piso` | `Piso` | FK al piso |

#### `Producto`

Catálogo de productos del piso. Los productos son reutilizables entre listas.

| Campo | Tipo | Descripción |
|---|---|---|
| `nombre` | `String` | Nombre del producto |
| `categoria` | `String` | Categoría libre (p.ej. `"Lácteos"`, `"Limpieza"`) |
| `piso` | `Piso` | FK al piso |

#### `ItemListaCompra`

Línea de una lista de la compra: qué producto, cuánto y quién lo pidió.

| Campo | Tipo | Descripción |
|---|---|---|
| `lista` | `ListaCompra` | FK a la lista |
| `producto` | `Producto` | FK al producto del catálogo |
| `cantidad` | `int` | Unidades solicitadas |
| `comprado` | `boolean` | Si ya se ha adquirido |
| `solicitadoPor` | `User` | FK al miembro que lo añadió |

#### `Compra`

Registro de una compra efectuada: quién fue, cuánto gastó y qué lista cubre. Puede vincularse opcionalmente con un `Gasto` si se quiere reflejar el desembolso en el módulo de gastos.

| Campo | Tipo | Descripción |
|---|---|---|
| `fecha` | `LocalDate` | Fecha de la compra |
| `importeTotal` | `BigDecimal` | Importe total gastado |
| `comprador` | `User` | FK al miembro que fue a comprar |
| `lista` | `ListaCompra` | FK a la lista cubierta |
| `piso` | `Piso` | FK al piso |
| `gasto` | `Gasto` | FK al gasto generado (puede ser `null`) |

---

### Módulo de alertas

#### `Alerta`

Notificación interna del piso, de lectura grupal.

| Campo | Tipo | Descripción |
|---|---|---|
| `piso` | `Piso` | FK al piso destinatario |
| `mensaje` | `String` | Texto de la alerta |
| `fecha` | `LocalDateTime` | Cuándo se generó |
| `tipo` | `TipoAlerta` | `INFO`, `URGENTE` o `RECORDATORIO` |
| `leida` | `boolean` | Si ya ha sido vista |

---

## 4. Enumeraciones

| Enum | Valores | Usada en |
|---|---|---|
| `RolPiso` | `PROPIETARIO`, `INQUILINO` | `MiembroPiso` |
| `EstadoGasto` | `PENDIENTE`, `LIQUIDADO` | `Gasto` |
| `TipoTarea` | `PUNTUAL`, `RECURRENTE` | `Tarea` |
| `EstadoAsistencia` | `PENDIENTE`, `CONFIRMADO`, `RECHAZADO` | `AsistenciaEvento` |
| `TipoBloque` | `TRABAJO`, `CLASES`, `ENTRENAMIENTO`, `OTRO` | `BloqueHorario` |
| `TipoAlerta` | `INFO`, `URGENTE`, `RECORDATORIO` | `Alerta` |

Todos los enums se persisten como `String` (`@Enumerated(EnumType.STRING)`) para que los valores en la base de datos sean legibles sin necesitar el código fuente.

---

## 5. Generación de IDs y datos semilla

### Dos estrategias coexisten

| Entidades | Estrategia | Mecanismo H2 |
|---|---|---|
| `User`, `Topic`, `Message` (plantilla) | `SEQUENCE` — generador `gen` | Secuencia `PUBLIC.GEN` compartida |
| Todas las demás (dominio SyncFlat) | `IDENTITY` | Autoincremento por columna |

### Problema con `import.sql` e H2 IDENTITY

`import.sql` inserta filas con IDs explícitos (`VALUES (1, ...)`). H2 no avanza el contador IDENTITY cuando se insertan IDs de forma manual, de modo que el primer `persist()` desde JPA generaría id `1` de nuevo, colisionando con los datos semilla.

**Solución aplicada** al final de `import.sql`:

```sql
-- Para entidades SEQUENCE (plantilla):
ALTER SEQUENCE "PUBLIC"."GEN" RESTART WITH 1024;

-- Para cada entidad IDENTITY del dominio:
ALTER TABLE Piso              ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Miembro_Piso      ALTER COLUMN id RESTART WITH 1024;
-- ... (resto de tablas, mismo patrón)
```

El valor `1024` deja margen suficiente por encima de cualquier ID fijo de los datos de prueba, que no superan `2`.

### Orden de inicialización

La propiedad `spring.jpa.defer-datasource-initialization=true` en `application.properties` garantiza que Hibernate genera el DDL (crea las tablas) antes de que Spring ejecute `import.sql`. Sin esta propiedad, `import.sql` se ejecutaría sobre un esquema vacío y fallaría con errores de tabla inexistente.

---

## 6. Relaciones destacadas (resumen)

```
Piso  1──n  MiembroPiso  n──1  User
Piso  1──n  Gasto  1──n  ParticipanteGasto  n──1  User
Piso  1──n  Tarea  1──n  AsignacionTarea  n──1  User
Piso  1──n  Evento  1──n  AsistenciaEvento  n──1  User
Piso  1──n  ListaCompra  1──n  ItemListaCompra  n──1  Producto
Piso  1──n  Alerta
Piso  1──n  Liquidacion
User  1──n  Ausencia
User  1──n  BloqueHorario
```
