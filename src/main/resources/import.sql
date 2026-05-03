-- =============================================================
-- SyncFlat: Seed de escenarios funcionales (ampliado)
-- =============================================================
-- Usuarios: a (ADMIN, piso1), b (MIEMBRO, piso1), c (sin piso),
--           d (piso2), e (MIEMBRO, piso1)
-- Pisos: 1 = Piso Moncloa (4 hab, 3 ocupadas, 1 libre)
--        2 = Piso Retiro (mínimo, cross-piso)
-- Contraseña de todos: aa (bcrypt)
-- =============================================================

-- -----------------------------------------------
-- Usuarios
-- -----------------------------------------------
INSERT INTO IWUser (id, enabled, roles, username, password, first_name, last_name)
VALUES (1, TRUE, 'ADMIN,USER', 'a',
    '{bcrypt}$2a$10$2BpNTbrsarbHjNsUWgzfNubJqBRf.0Vz9924nRSHBqlbPKerkgX.W',
    'Ana', 'Garcia');
INSERT INTO IWUser (id, enabled, roles, username, password, first_name, last_name)
VALUES (2, TRUE, 'USER', 'b',
    '{bcrypt}$2a$10$2BpNTbrsarbHjNsUWgzfNubJqBRf.0Vz9924nRSHBqlbPKerkgX.W',
    'Boris', 'Lopez');
-- Usuario sin piso: flujo crear/unirse (piso.feature, habitaciones_admin.feature)
INSERT INTO IWUser (id, enabled, roles, username, password, first_name, last_name)
VALUES (3, TRUE, 'USER', 'c',
    '{bcrypt}$2a$10$2BpNTbrsarbHjNsUWgzfNubJqBRf.0Vz9924nRSHBqlbPKerkgX.W',
    'Carlos', 'Ruiz');
-- Usuario exclusivo Piso 2: tests de acceso cross-piso
INSERT INTO IWUser (id, enabled, roles, username, password, first_name, last_name)
VALUES (4, TRUE, 'USER', 'd',
    '{bcrypt}$2a$10$2BpNTbrsarbHjNsUWgzfNubJqBRf.0Vz9924nRSHBqlbPKerkgX.W',
    'Diana', 'Martin');
-- Tercer miembro del piso principal: hab 4 (deja hab 3 libre para demo)
INSERT INTO IWUser (id, enabled, roles, username, password, first_name, last_name)
VALUES (5, TRUE, 'USER', 'e',
    '{bcrypt}$2a$10$2BpNTbrsarbHjNsUWgzfNubJqBRf.0Vz9924nRSHBqlbPKerkgX.W',
    'Elena', 'Vega');

ALTER SEQUENCE "PUBLIC"."GEN" RESTART WITH 1024;

-- -----------------------------------------------
-- Pisos
-- -----------------------------------------------
-- Piso principal: 4 habitaciones, 3 ocupadas (hab 3 libre)
INSERT INTO Piso (id, nombre, direccion, fecha_creacion, codigo_invitacion, num_habitaciones)
VALUES (1, 'Piso Moncloa', 'Calle Princesa 25, 3B, Madrid', '2025-09-01', 'MONC01', 4);
-- Piso mínimo para tests de seguridad multi-piso
INSERT INTO Piso (id, nombre, direccion, fecha_creacion, codigo_invitacion, num_habitaciones)
VALUES (2, 'Piso Retiro', 'Calle Alcala 10, 1A, Madrid', '2025-09-01', 'RETR01', 2);

-- -----------------------------------------------
-- MiembroPiso
-- -----------------------------------------------
-- INVARIANTES: IDs 1, 2, 3 referenciados directamente por los tests
INSERT INTO Miembro_Piso (id, piso_id, usuario_id, rol_en_piso, fecha_ingreso, fecha_salida, num_habitacion)
VALUES (1, 1, 1, 'ADMIN',   '2025-09-01', NULL, 1);
INSERT INTO Miembro_Piso (id, piso_id, usuario_id, rol_en_piso, fecha_ingreso, fecha_salida, num_habitacion)
VALUES (2, 1, 2, 'MIEMBRO', '2025-09-15', NULL, 2);
-- 'd' pertenece solo a Piso 2 → tests de acceso cross-piso
INSERT INTO Miembro_Piso (id, piso_id, usuario_id, rol_en_piso, fecha_ingreso, fecha_salida, num_habitacion)
VALUES (3, 2, 4, 'ADMIN',   '2025-09-01', NULL, 1);
-- 'e' en Piso 1, hab 4 → hab 3 queda libre para el panel de estado
INSERT INTO Miembro_Piso (id, piso_id, usuario_id, rol_en_piso, fecha_ingreso, fecha_salida, num_habitacion)
VALUES (4, 1, 5, 'MIEMBRO', '2025-09-20', NULL, 4);

-- -----------------------------------------------
-- Productos
-- -----------------------------------------------
-- Productos base (invariantes por los tests de compra)
INSERT INTO Producto (id, nombre, categoria, piso_id)
VALUES (1, 'Leche entera',           'Alimentacion', 1);
INSERT INTO Producto (id, nombre, categoria, piso_id)
VALUES (2, 'Papel higienico',        'Limpieza',     1);
INSERT INTO Producto (id, nombre, categoria, piso_id)
VALUES (3, 'Detergente loza',        'Limpieza',     1);
INSERT INTO Producto (id, nombre, categoria, piso_id)
VALUES (4, 'Pan de molde',           'Alimentacion', 1);
-- Catálogo ampliado para demo visual
INSERT INTO Producto (id, nombre, categoria, piso_id)
VALUES (5, 'Aceite de oliva virgen', 'Alimentacion', 1);
INSERT INTO Producto (id, nombre, categoria, piso_id)
VALUES (6, 'Arroz redondo',          'Alimentacion', 1);
INSERT INTO Producto (id, nombre, categoria, piso_id)
VALUES (7, 'Tomate frito',           'Alimentacion', 1);
INSERT INTO Producto (id, nombre, categoria, piso_id)
VALUES (8, 'Gel de ducha',           'Higiene',      1);
INSERT INTO Producto (id, nombre, categoria, piso_id)
VALUES (9, 'Suavizante ropa',        'Limpieza',     1);
INSERT INTO Producto (id, nombre, categoria, piso_id)
VALUES (10, 'Cafe molido',           'Alimentacion', 1);

-- -----------------------------------------------
-- Gastos
-- -----------------------------------------------
-- 1. PENDIENTE parcial: 'a' pagó, 'b' todavía debe su parte
--    INVARIANTE: id=1, participante(1)=pagado=TRUE, participante(2)=pagado=FALSE
INSERT INTO Gasto (id, concepto, importe, fecha, pagador_id, piso_id, estado)
VALUES (1, 'Factura de internet - octubre', 45.00, '2025-10-05', 1, 1, 'PENDIENTE');

-- 2. PENDIENTE parcial: 'e' pagó; 'a' no ha liquidado, 'b' ya lo hizo
INSERT INTO Gasto (id, concepto, importe, fecha, pagador_id, piso_id, estado)
VALUES (2, 'Compra del supermercado - noviembre', 87.60, '2025-11-02', 5, 1, 'PENDIENTE');

-- 3. LIQUIDADO: 'b' pagó la factura del agua; todos saldados
INSERT INTO Gasto (id, concepto, importe, fecha, pagador_id, piso_id, estado)
VALUES (3, 'Factura del agua - septiembre', 54.00, '2025-09-30', 2, 1, 'LIQUIDADO');

-- 4. PENDIENTE generado desde compra (vinculado a Compra 2 / Lista 3)
INSERT INTO Gasto (id, concepto, importe, fecha, pagador_id, piso_id, estado)
VALUES (4, 'Compra lista de febrero', 42.00, '2026-02-28', 1, 1, 'PENDIENTE');

-- 5. PENDIENTE generado desde compra (vinculado a Compra 3 / Lista 5, a+b)
INSERT INTO Gasto (id, concepto, importe, fecha, pagador_id, piso_id, estado)
VALUES (5, 'Productos de higiene - compra de marzo', 27.50, '2026-03-15', 2, 1, 'PENDIENTE');

-- 6. PENDIENTE común a todos: 'a' adelantó la luz; b y e aún no han pagado
INSERT INTO Gasto (id, concepto, importe, fecha, pagador_id, piso_id, estado)
VALUES (6, 'Factura de la luz - mayo', 95.40, '2026-05-01', 1, 1, 'PENDIENTE');

-- -----------------------------------------------
-- ParticipanteGasto
-- -----------------------------------------------
-- Gasto 1: invariante (id=1 pagado=TRUE, id=2 pagado=FALSE)
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (1, 1, 1, 22.50, TRUE);
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (2, 1, 2, 22.50, FALSE);

-- Gasto 2: e pagó; b ya liquidó; a debe a e
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (3, 2, 1, 29.20, FALSE);
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (4, 2, 2, 29.20, TRUE);
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (5, 2, 5, 29.20, TRUE);

-- Gasto 3: LIQUIDADO, todos pagados
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (6, 3, 1, 18.00, TRUE);
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (7, 3, 2, 18.00, TRUE);
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (8, 3, 5, 18.00, TRUE);

-- Gasto 4: a+e, ambos saldados (historial limpio)
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (9,  4, 1, 21.00, TRUE);
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (10, 4, 5, 21.00, TRUE);

-- Gasto 5: b+a, compra higiene; b pagó (pagador), a aún no ha devuelto
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (11, 5, 2, 13.75, TRUE);
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (12, 5, 1, 13.75, FALSE);

-- Gasto 6: factura luz, a pagó (pagador); b y e deben su parte
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (13, 6, 1, 31.80, TRUE);
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (14, 6, 2, 31.80, FALSE);
INSERT INTO Participante_Gasto (id, gasto_id, usuario_id, importe_asignado, pagado)
VALUES (15, 6, 5, 31.80, FALSE);

-- -----------------------------------------------
-- ListaCompra
-- -----------------------------------------------
-- INVARIANTES: id=1 y id=2 completada=FALSE, referenciados por registrar_compra.feature
INSERT INTO Lista_Compra (id, nombre, fecha_creacion, completada, piso_id)
VALUES (1, 'Compra semana 42',            '2025-10-13', FALSE, 1);
INSERT INTO Lista_Compra (id, nombre, fecha_creacion, completada, piso_id)
VALUES (2, 'Lista supermercado',          '2025-10-14', FALSE, 1);
-- Lista cerrada con participantes concretos y gasto asociado (historial)
INSERT INTO Lista_Compra (id, nombre, fecha_creacion, completada, piso_id)
VALUES (3, 'Lista de febrero',            '2026-02-01', TRUE,  1);
-- Lista activa pública para semana en curso (todos los miembros)
INSERT INTO Lista_Compra (id, nombre, fecha_creacion, completada, piso_id)
VALUES (4, 'Lista semanal - semana 19',   '2026-05-04', FALSE, 1);
-- Lista cerrada con participantes específicos a+b y gasto generado
INSERT INTO Lista_Compra (id, nombre, fecha_creacion, completada, piso_id)
VALUES (5, 'Productos de higiene - marzo','2026-03-10', TRUE,  1);

-- Participantes de Lista 3 (a y e; b no participó en esa compra)
INSERT INTO Lista_Participante (lista_id, usuario_id) VALUES (3, 1);
INSERT INTO Lista_Participante (lista_id, usuario_id) VALUES (3, 5);
-- Participantes de Lista 5 (a y b; e no participó)
INSERT INTO Lista_Participante (lista_id, usuario_id) VALUES (5, 1);
INSERT INTO Lista_Participante (lista_id, usuario_id) VALUES (5, 2);

-- -----------------------------------------------
-- ItemListaCompra
-- -----------------------------------------------
-- INVARIANTE: id=1 y id=2 en Lista 1 (compra.feature toggle e registrar_compra.feature)
INSERT INTO Item_Lista_Compra (id, lista_id, producto_id, cantidad, comprado, solicitado_por_id)
VALUES (1, 1, 1, 2, FALSE, 1);
INSERT INTO Item_Lista_Compra (id, lista_id, producto_id, cantidad, comprado, solicitado_por_id)
VALUES (2, 1, 2, 1, FALSE, 2);
-- Lista 2 activa: ítems mezclados (marcado + pendiente)
INSERT INTO Item_Lista_Compra (id, lista_id, producto_id, cantidad, comprado, solicitado_por_id)
VALUES (3, 2, 3, 1, FALSE, 2);
INSERT INTO Item_Lista_Compra (id, lista_id, producto_id, cantidad, comprado, solicitado_por_id)
VALUES (4, 2, 4, 2, TRUE,  5);
-- Lista 3 cerrada: todos comprados
INSERT INTO Item_Lista_Compra (id, lista_id, producto_id, cantidad, comprado, solicitado_por_id)
VALUES (5, 3, 3, 1, TRUE, 1);
INSERT INTO Item_Lista_Compra (id, lista_id, producto_id, cantidad, comprado, solicitado_por_id)
VALUES (6, 3, 4, 2, TRUE, 5);
-- Lista 4 activa (semana 19): variedad de ítems marcados y pendientes
INSERT INTO Item_Lista_Compra (id, lista_id, producto_id, cantidad, comprado, solicitado_por_id)
VALUES (7,  4, 5, 1, FALSE, 1);
INSERT INTO Item_Lista_Compra (id, lista_id, producto_id, cantidad, comprado, solicitado_por_id)
VALUES (8,  4, 6, 2, TRUE,  2);
INSERT INTO Item_Lista_Compra (id, lista_id, producto_id, cantidad, comprado, solicitado_por_id)
VALUES (9,  4, 7, 3, FALSE, 5);
INSERT INTO Item_Lista_Compra (id, lista_id, producto_id, cantidad, comprado, solicitado_por_id)
VALUES (10, 4, 2, 4, FALSE, 1);
INSERT INTO Item_Lista_Compra (id, lista_id, producto_id, cantidad, comprado, solicitado_por_id)
VALUES (11, 4, 1, 2, TRUE,  2);
INSERT INTO Item_Lista_Compra (id, lista_id, producto_id, cantidad, comprado, solicitado_por_id)
VALUES (12, 4, 10,1, FALSE, 5);
-- Lista 5 cerrada (a+b): todos comprados
INSERT INTO Item_Lista_Compra (id, lista_id, producto_id, cantidad, comprado, solicitado_por_id)
VALUES (13, 5, 8, 2, TRUE, 1);
INSERT INTO Item_Lista_Compra (id, lista_id, producto_id, cantidad, comprado, solicitado_por_id)
VALUES (14, 5, 9, 1, TRUE, 2);

-- -----------------------------------------------
-- Compra
-- -----------------------------------------------
-- Compra 1: histórico sin gasto (lista 1 sigue activa para los tests)
INSERT INTO Compra (id, fecha, importe_total, comprador_id, lista_id, piso_id, gasto_id)
VALUES (1, '2025-10-14', 12.75, 2, 1, 1, NULL);
-- Compra 2: lista cerrada a+e con gasto compartido generado
INSERT INTO Compra (id, fecha, importe_total, comprador_id, lista_id, piso_id, gasto_id)
VALUES (2, '2026-02-28', 42.00, 1, 3, 1, 4);
-- Compra 3: lista cerrada a+b con gasto de higiene generado
INSERT INTO Compra (id, fecha, importe_total, comprador_id, lista_id, piso_id, gasto_id)
VALUES (3, '2026-03-15', 27.50, 2, 5, 1, 5);

-- -----------------------------------------------
-- Tarea
-- -----------------------------------------------
-- INVARIANTE: id=1 'Fregar el suelo' RECURRENTE SEMANAL fechaLimite=2025-10-19
--   → AsignacionTarea(1) asignada a 'b'; los tests la completan y validan
INSERT INTO Tarea (id, nombre, descripcion, tipo, frecuencia, fecha_limite, piso_id)
VALUES (1, 'Fregar el suelo', 'Fregar cocina y salon', 'RECURRENTE', 'SEMANAL', '2025-10-19', 1);

-- Tarea 2: recurrente con historial (ocurrencia validada + ocurrencia pendiente de validar)
INSERT INTO Tarea (id, nombre, descripcion, tipo, frecuencia, fecha_limite, piso_id)
VALUES (2, 'Limpiar el bano', 'Limpieza semanal del bano completo', 'RECURRENTE', 'SEMANAL', '2026-05-10', 1);

-- Tarea 3: puntual urgente (vence pronto)
INSERT INTO Tarea (id, nombre, descripcion, tipo, frecuencia, fecha_limite, piso_id)
VALUES (3, 'Pagar el alquiler', 'Transferencia mensual al arrendador', 'PUNTUAL', NULL, '2026-05-05', 1);

-- Tarea 4: recurrente con ocurrencia actual pendiente y anterior validada
INSERT INTO Tarea (id, nombre, descripcion, tipo, frecuencia, fecha_limite, piso_id)
VALUES (4, 'Sacar la basura', 'Cubos al portal los miercoles antes de las 20h', 'RECURRENTE', 'SEMANAL', '2026-05-07', 1);

-- Tarea 5: puntual completada, pendiente de validar
INSERT INTO Tarea (id, nombre, descripcion, tipo, frecuencia, fecha_limite, piso_id)
VALUES (5, 'Pasar la aspiradora', 'Salon, pasillos y habitaciones comunes', 'PUNTUAL', NULL, '2026-05-20', 1);

-- Tarea 6: puntual, sin completar, asignada a 'e'
INSERT INTO Tarea (id, nombre, descripcion, tipo, frecuencia, fecha_limite, piso_id)
VALUES (6, 'Revisar las facturas del mes', 'Comprobar luz, agua e internet', 'PUNTUAL', NULL, '2026-05-15', 1);

-- -----------------------------------------------
-- AsignacionTarea
-- -----------------------------------------------
-- INVARIANTE: id=1 tarea=1 usuario=b PENDIENTE
--   → tareas.feature la completa; validacion_tareas.feature la valida
INSERT INTO Asignacion_Tarea (id, tarea_id, usuario_id, fecha_asignacion, fecha_completada, validada, validador_id)
VALUES (1, 1, 2, '2025-10-13', NULL,         FALSE, NULL);

-- Tarea 2: ocurrencia anterior validada por 'a'
INSERT INTO Asignacion_Tarea (id, tarea_id, usuario_id, fecha_asignacion, fecha_completada, validada, validador_id)
VALUES (2, 2, 1, '2026-04-26', '2026-04-26', TRUE,  2);

-- Tarea 2: ocurrencia actual completada por 'e', pendiente de validar
INSERT INTO Asignacion_Tarea (id, tarea_id, usuario_id, fecha_asignacion, fecha_completada, validada, validador_id)
VALUES (3, 2, 5, '2026-05-03', '2026-05-03', FALSE, NULL);

-- Tarea 3: pendiente, asignada a 'a'
INSERT INTO Asignacion_Tarea (id, tarea_id, usuario_id, fecha_asignacion, fecha_completada, validada, validador_id)
VALUES (4, 3, 1, '2026-05-01', NULL,         FALSE, NULL);

-- Tarea 4: ocurrencia anterior validada por 'a'
INSERT INTO Asignacion_Tarea (id, tarea_id, usuario_id, fecha_asignacion, fecha_completada, validada, validador_id)
VALUES (5, 4, 5, '2026-04-26', '2026-04-26', TRUE,  1);

-- Tarea 4: ocurrencia actual pendiente, asignada a 'b'
INSERT INTO Asignacion_Tarea (id, tarea_id, usuario_id, fecha_asignacion, fecha_completada, validada, validador_id)
VALUES (6, 4, 2, '2026-05-03', NULL,         FALSE, NULL);

-- Tarea 5: completada por 'a', pendiente de que otro la valide
INSERT INTO Asignacion_Tarea (id, tarea_id, usuario_id, fecha_asignacion, fecha_completada, validada, validador_id)
VALUES (7, 5, 1, '2026-05-01', '2026-05-03', FALSE, NULL);

-- Tarea 6: pendiente, asignada a 'e'
INSERT INTO Asignacion_Tarea (id, tarea_id, usuario_id, fecha_asignacion, fecha_completada, validada, validador_id)
VALUES (8, 6, 5, '2026-05-01', NULL,         FALSE, NULL);

-- -----------------------------------------------
-- Evento
-- -----------------------------------------------
-- INVARIANTE: id=1 Cena de bienvenida PROPUESTO 2025-09-20 20:00-23:00
--   → calendario.feature lo aprueba, rechaza y testa conflictos con bloque id=1
INSERT INTO Evento (id, titulo, descripcion, fecha_inicio, fecha_fin, creador_id, piso_id, estado)
VALUES (1, 'Cena de bienvenida', 'Cena para conocernos todos',
        '2025-09-20 20:00:00', '2025-09-20 23:00:00', 1, 1, 'PROPUESTO');

-- Evento 2: APROBADO (demo de flujo completo de aprobación)
INSERT INTO Evento (id, titulo, descripcion, fecha_inicio, fecha_fin, creador_id, piso_id, estado)
VALUES (2, 'Revision de gastos del mes', 'Puesta en comun de facturas pendientes',
        '2026-05-10 19:00:00', '2026-05-10 20:00:00', 1, 1, 'APROBADO');

-- Evento 3: RECHAZADO (demo de evento descartado)
INSERT INTO Evento (id, titulo, descripcion, fecha_inicio, fecha_fin, creador_id, piso_id, estado)
VALUES (3, 'Barbacoa en el parque', 'Propuesta descartada por lluvia',
        '2026-05-24 13:00:00', '2026-05-24 18:00:00', 2, 1, 'RECHAZADO');

-- Evento 4: APROBADO, de todos los miembros; provoca conflicto con bloque 10 (b/Baloncesto 20:00-22:00)
INSERT INTO Evento (id, titulo, descripcion, fecha_inicio, fecha_fin, creador_id, piso_id, estado)
VALUES (4, 'Cena de piso', 'Cena mensual del piso en casa',
        '2026-05-08 21:00:00', '2026-05-08 23:30:00', 1, 1, 'APROBADO');

-- Evento 5: PROPUESTO, solo b y e (evento parcial); sin conflictos en ese horario
INSERT INTO Evento (id, titulo, descripcion, fecha_inicio, fecha_fin, creador_id, piso_id, estado)
VALUES (5, 'Ver el partido de Champions', 'Final de la Champions en casa',
        '2026-05-15 20:00:00', '2026-05-15 22:30:00', 2, 1, 'PROPUESTO');

-- Evento 6: PROPUESTO, todos; sin conflicto (bloque 14 de e termina antes de que empiece)
INSERT INTO Evento (id, titulo, descripcion, fecha_inicio, fecha_fin, creador_id, piso_id, estado)
VALUES (6, 'Reunion del piso', 'Puesta en comun de normas y gastos compartidos',
        '2026-05-20 19:30:00', '2026-05-20 21:00:00', 1, 1, 'PROPUESTO');

-- -----------------------------------------------
-- AsistenciaEvento
-- -----------------------------------------------
-- INVARIANTE: id=1 y id=2 para Evento 1 (calendario.feature)
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (1, 1, 1, 'CONFIRMADO');
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (2, 1, 2, 'PENDIENTE');

-- Evento 2 (APROBADO, todos)
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (3, 2, 1, 'CONFIRMADO');
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (4, 2, 2, 'CONFIRMADO');
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (5, 2, 5, 'PENDIENTE');

-- Evento 3 (RECHAZADO, todos)
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (6, 3, 1, 'CONFIRMADO');
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (7, 3, 2, 'RECHAZADO');
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (8, 3, 5, 'RECHAZADO');

-- Evento 4 (Cena de piso, APROBADO, todos confirmados)
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (9,  4, 1, 'CONFIRMADO');
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (10, 4, 2, 'CONFIRMADO');
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (11, 4, 5, 'CONFIRMADO');

-- Evento 5 (partido Champions, solo b y e)
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (12, 5, 2, 'PENDIENTE');
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (13, 5, 5, 'PENDIENTE');

-- Evento 6 (Reunión del piso, todos pendientes)
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (14, 6, 1, 'PENDIENTE');
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (15, 6, 2, 'PENDIENTE');
INSERT INTO Asistencia_Evento (id, evento_id, usuario_id, estado)
VALUES (16, 6, 5, 'PENDIENTE');

-- -----------------------------------------------
-- BloqueHorario
-- -----------------------------------------------
-- INVARIANTE id=1: bloque de 'b' que solapa con Evento 1 (calendario.feature conflictos)
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (1, 2, 'ENTRENAMIENTO', 'Futbol sala',
        '2025-09-20 19:30:00', '2025-09-20 21:30:00');

-- INVARIANTE id=2: bloque de 'a'; calendario.feature lo elimina vía DELETE /bloque/2
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (2, 1, 'TRABAJO', 'Trabajo en remoto',
        '2025-09-20 09:00:00', '2025-09-20 14:00:00');

-- Bloque de 'a' sin conflicto con Evento 1 (otro día)
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (3, 1, 'CLASES', 'Clases de ingles',
        '2025-09-22 16:00:00', '2025-09-22 18:00:00');

-- Bloque de 'e' (demo de disponibilidad del tercer miembro)
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (4, 5, 'TRABAJO', 'Jornada laboral completa',
        '2026-05-12 09:00:00', '2026-05-12 17:00:00');

-- Bloque de 'b' clase de guitarra
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (5, 2, 'CLASES', 'Clase de guitarra',
        '2026-05-15 17:00:00', '2026-05-15 19:00:00');

-- Bloques ampliados de 'a'
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (6, 1, 'TRABAJO', 'Oficina - reunion de equipo',
        '2026-05-06 09:00:00', '2026-05-06 14:00:00');
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (7, 1, 'ENTRENAMIENTO', 'Gimnasio matutino',
        '2026-05-07 07:00:00', '2026-05-07 08:30:00');
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (8, 1, 'CLASES', 'Clases de frances',
        '2026-05-13 18:00:00', '2026-05-13 20:00:00');

-- Bloques ampliados de 'b'
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (9, 2, 'TRABAJO', 'Trabajo en remoto - sprint',
        '2026-05-06 10:00:00', '2026-05-06 18:00:00');
-- Bloque 10: CONFLICTO real con Evento 4 (Cena de piso 21:00-23:30 el 08/05)
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (10, 2, 'ENTRENAMIENTO', 'Entrenamiento de baloncesto',
        '2026-05-08 20:00:00', '2026-05-08 22:00:00');
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (11, 2, 'OTRO', 'Visita al medico',
        '2026-05-14 10:00:00', '2026-05-14 11:00:00');

-- Bloques ampliados de 'e'
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (12, 5, 'TRABAJO', 'Jornada laboral en oficina',
        '2026-05-07 09:00:00', '2026-05-07 17:00:00');
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (13, 5, 'ENTRENAMIENTO', 'Pilates',
        '2026-05-09 19:00:00', '2026-05-09 20:00:00');
-- Bloque 14: termina a las 19:00; Evento 6 empieza a las 19:30 → sin conflicto
INSERT INTO Bloque_Horario (id, usuario_id, tipo, descripcion, inicio, fin)
VALUES (14, 5, 'CLASES', 'Clase de cocina',
        '2026-05-20 17:00:00', '2026-05-20 19:00:00');

-- -----------------------------------------------
-- Alerta
-- -----------------------------------------------
-- INVARIANTE id=1: texto exacto buscado por alertas.feature ('Revision del gas')
INSERT INTO Alerta (id, piso_id, mensaje, fecha, tipo, leida)
VALUES (1, 1, 'Revision del gas programada para el lunes',
        '2025-10-10 09:00:00', 'INFO', FALSE);

-- Alerta ya leída (demo de estado leida=TRUE)
INSERT INTO Alerta (id, piso_id, mensaje, fecha, tipo, leida)
VALUES (2, 1, 'Recordad pagar el alquiler antes del dia 5',
        '2026-04-30 08:00:00', 'RECORDATORIO', TRUE);

-- Alerta urgente activa
INSERT INTO Alerta (id, piso_id, mensaje, fecha, tipo, leida)
VALUES (3, 1, 'Fuga de agua en el bano. Avisar al casero urgente',
        '2026-05-02 22:00:00', 'URGENTE', FALSE);

-- Alerta informativa pendiente
INSERT INTO Alerta (id, piso_id, mensaje, fecha, tipo, leida)
VALUES (4, 1, 'La revision anual del ascensor es el proximo martes',
        '2026-05-03 10:00:00', 'INFO', FALSE);

-- Alertas vinculadas a acciones recientes del piso
INSERT INTO Alerta (id, piso_id, mensaje, fecha, tipo, leida)
VALUES (5, 1, 'Nueva lista de la compra creada para esta semana',
        '2026-05-04 09:00:00', 'INFO', FALSE);
INSERT INTO Alerta (id, piso_id, mensaje, fecha, tipo, leida)
VALUES (6, 1, 'Nuevo gasto registrado: Factura de la luz 95.40 EUR',
        '2026-05-01 11:00:00', 'INFO', FALSE);
INSERT INTO Alerta (id, piso_id, mensaje, fecha, tipo, leida)
VALUES (7, 1, 'Tarea asignada: Sacar la basura (vence el 07/05)',
        '2026-05-03 12:00:00', 'RECORDATORIO', FALSE);

-- =============================================================
-- Reinicio de contadores IDENTITY para evitar colisiones de PK
-- al persistir nuevas entidades desde JPA.
-- =============================================================
ALTER TABLE Piso               ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Miembro_Piso       ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Evento             ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Asistencia_Evento  ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Alerta             ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Gasto              ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Participante_Gasto ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Producto           ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Lista_Compra       ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Item_Lista_Compra  ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Compra             ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Tarea              ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Asignacion_Tarea   ALTER COLUMN id RESTART WITH 1024;
ALTER TABLE Bloque_Horario     ALTER COLUMN id RESTART WITH 1024;
