-- =============================================================
-- SyncFlat: Datos iniciales de prueba
-- =============================================================

-- -----------------------------------------------
-- Usuarios (ya existentes, IDs asignados via SEQUENCE)
-- -----------------------------------------------
INSERT INTO IWUser (id, enabled, roles, username, password, first_name, last_name)
VALUES (1, TRUE, 'ADMIN,USER', 'a',
    '{bcrypt}$2a$10$2BpNTbrsarbHjNsUWgzfNubJqBRf.0Vz9924nRSHBqlbPKerkgX.W',
    'Ana', 'Garcia');
INSERT INTO IWUser (id, enabled, roles, username, password, first_name, last_name)
VALUES (2, TRUE, 'USER', 'b',
    '{bcrypt}$2a$10$2BpNTbrsarbHjNsUWgzfNubJqBRf.0Vz9924nRSHBqlbPKerkgX.W',
    'Boris', 'Lopez');
-- Usuario de prueba sin piso (usado en tests Karate)
INSERT INTO IWUser (id, enabled, roles, username, password, first_name, last_name)
VALUES (3, TRUE, 'USER', 'c',
    '{bcrypt}$2a$10$2BpNTbrsarbHjNsUWgzfNubJqBRf.0Vz9924nRSHBqlbPKerkgX.W',
    'Carlos', 'Ruiz');
-- Usuario exclusivo de Piso 2, para tests de acceso cross-piso
INSERT INTO IWUser (id, enabled, roles, username, password, first_name, last_name)
VALUES (4, TRUE, 'USER', 'd',
    '{bcrypt}$2a$10$2BpNTbrsarbHjNsUWgzfNubJqBRf.0Vz9924nRSHBqlbPKerkgX.W',
    'Diana', 'Martin');

-- Reiniciar secuencia para User/Message/Topic
ALTER SEQUENCE "PUBLIC"."GEN" RESTART WITH 1024;

-- -----------------------------------------------
-- Piso
-- -----------------------------------------------
INSERT INTO Piso (id, nombre, direccion, fecha_creacion, codigo_invitacion, num_habitaciones)
VALUES (1, 'Piso Moncloa', 'Calle Princesa 25, 3B, Madrid', '2025-09-01', 'MONC01', 3);
-- Piso 2 para tests de acceso cross-piso (usuario 'd' solo pertenece aquí)
INSERT INTO Piso (id, nombre, direccion, fecha_creacion, codigo_invitacion, num_habitaciones)
VALUES (2, 'Piso Retiro', 'Calle Alcala 10, 1A, Madrid', '2025-09-01', 'RETR01', 2);

-- -----------------------------------------------
-- MiembroPiso
-- -----------------------------------------------
INSERT INTO Miembro_Piso (id, piso_id, usuario_id, rol_en_piso, fecha_ingreso, fecha_salida, num_habitacion)
VALUES (1, 1, 1, 'ADMIN', '2025-09-01', NULL, 1);
INSERT INTO Miembro_Piso (id, piso_id, usuario_id, rol_en_piso, fecha_ingreso, fecha_salida, num_habitacion)
VALUES (2, 1, 2, 'MIEMBRO', '2025-09-15', NULL, 2);
-- 'd' pertenece solo a Piso 2 → no tiene acceso a tareas/asignaciones de Piso 1
INSERT INTO Miembro_Piso (id, piso_id, usuario_id, rol_en_piso, fecha_ingreso, fecha_salida, num_habitacion)
VALUES (3, 2, 4, 'ADMIN', '2025-09-01', NULL, 1);

-- -----------------------------------------------
-- Evento
-- -----------------------------------------------
INSERT INTO Evento (id, titulo, descripcion, fecha_inicio, fecha_fin, creador_id, piso_id, estado)
VALUES (1, 'Cena de bienvenida', 'Cena para conocernos todos', '2025-09-20 20:00:00', '2025-09-20 23:00:00', 1, 1, 'PROPUESTO');

-- -----------------------------------------------
-- AsistenciaEvento
-- -----------------------------------------------
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (1, 1, 1, 'CONFIRMADO');
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (2, 1, 2, 'PENDIENTE');

-- -----------------------------------------------
-- Alerta
-- -----------------------------------------------
INSERT INTO Alerta (id, piso_id, mensaje, fecha, tipo, leida)
VALUES (1, 1, 'Revision del gas programada para el lunes', '2025-10-10 09:00:00', 'INFO', FALSE);

-- -----------------------------------------------
-- Gasto
-- -----------------------------------------------
INSERT INTO Gasto (id, concepto, importe, fecha, pagador_id, piso_id, estado)
VALUES (1, 'Factura de internet - octubre', 45.00, '2025-10-05', 1, 1, 'PENDIENTE');

-- -----------------------------------------------
-- ParticipanteGasto
-- -----------------------------------------------
-- pagador_id=1 (usuario 'a') → su parte queda pagada automáticamente (invariante del dominio)
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (1, 1, 1, 22.50, TRUE);
-- usuario 'b' todavía debe su parte a 'a'
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (2, 1, 2, 22.50, FALSE);

-- -----------------------------------------------
-- Producto
-- -----------------------------------------------
INSERT INTO Producto (id, nombre, categoria, piso_id)
VALUES (1, 'Leche entera', 'Alimentacion', 1);
INSERT INTO Producto (id, nombre, categoria, piso_id)
VALUES (2, 'Papel higienico', 'Limpieza', 1);

-- -----------------------------------------------
-- ListaCompra
-- -----------------------------------------------
INSERT INTO Lista_Compra (id, nombre, fecha_creacion, completada, piso_id)
VALUES (1, 'Compra semana 42', '2025-10-13', FALSE, 1);
INSERT INTO Lista_Compra (id, nombre, fecha_creacion, completada, piso_id)
VALUES (2, 'Lista supermercado', '2025-10-14', FALSE, 1);

-- -----------------------------------------------
-- ItemListaCompra
-- -----------------------------------------------
INSERT INTO Item_Lista_Compra (id, lista_id, producto_id, cantidad, comprado, solicitado_por_id)
VALUES (1, 1, 1, 2, FALSE, 1);
INSERT INTO Item_Lista_Compra (id, lista_id, producto_id, cantidad, comprado, solicitado_por_id)
VALUES (2, 1, 2, 1, FALSE, 2);

-- -----------------------------------------------
-- Compra
-- -----------------------------------------------
INSERT INTO Compra (id, fecha, importe_total, comprador_id, lista_id, piso_id, gasto_id)
VALUES (1, '2025-10-14', 12.75, 2, 1, 1, NULL);

-- -----------------------------------------------
-- Tarea
-- -----------------------------------------------
INSERT INTO Tarea (id, nombre, descripcion, tipo, frecuencia, fecha_limite, piso_id)
VALUES (1, 'Fregar el suelo', 'Fregar cocina y salon', 'RECURRENTE', 'SEMANAL', '2025-10-19', 1);

-- -----------------------------------------------
-- AsignacionTarea
-- -----------------------------------------------
INSERT INTO Asignacion_Tarea (id, tarea_id, usuario_id, fecha_asignacion, fecha_completada, validada, validador_id)
VALUES (1, 1, 2, '2025-10-13', NULL, FALSE, NULL);

-- -----------------------------------------------
-- BloqueHorario
-- -----------------------------------------------
-- Bloque de 'b' que SOLAPA con "Cena de bienvenida" (2025-09-20 20:00-23:00)
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (1, 2, 'ENTRENAMIENTO', 'Fútbol sala', '2025-09-20 19:30:00', '2025-09-20 21:30:00');

-- Bloque de 'a' que NO solapa con el evento (termina antes de que empiece)
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (2, 1, 'TRABAJO', 'Trabajo en remoto', '2025-09-20 09:00:00', '2025-09-20 14:00:00');

-- Bloque de 'a' en otro día (sin conflicto)
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (3, 1, 'CLASES', 'Clases de inglés', '2025-09-22 16:00:00', '2025-09-22 18:00:00');

-- =============================================================
-- Reinicio de contadores IDENTITY para evitar colisiones de PK
-- al persistir nuevas entidades desde JPA.
-- Las tablas que usan GenerationType.IDENTITY no avanzan su
-- contador interno cuando import.sql inserta ids explícitos,
-- de modo que el primer persist() generaría id=1 de nuevo.
-- =============================================================
ALTER TABLE Piso              ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Miembro_Piso      ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Evento            ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Asistencia_Evento ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Alerta            ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Gasto             ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Participante_Gasto ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Producto          ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Lista_Compra      ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Item_Lista_Compra ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Compra            ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Tarea             ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Asignacion_Tarea  ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Bloque_Horario    ALTER COLUMN id RESTART WITH 1024;
