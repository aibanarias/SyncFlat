Feature: módulo de calendario y disponibilidad

Background:
  * url baseUrl
  * callonce read('helpers/login.feature') { username: 'a', password: 'aa' }

Scenario: crear bloque horario propio via endpoint JSON
  Given path 'modulos/calendario'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/calendario/bloque/crear'
  And form field tipo = 'TRABAJO'
  And form field descripcion = 'Prueba Karate'
  And form field inicio = '2026-07-01T09:00'
  And form field fin = '2026-07-01T14:00'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response.ok == true

Scenario: no se puede crear bloque con fin anterior a inicio
  Given path 'modulos/calendario'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/calendario/bloque/crear'
  And form field tipo = 'OTRO'
  And form field descripcion = 'Bloque inválido'
  And form field inicio = '2026-07-01T15:00'
  And form field fin = '2026-07-01T10:00'
  And form field _csrf = csrf
  When method post
  Then status 400
  And match response.ok == false
  And match response.message contains 'posterior'

Scenario: eliminar bloque horario propio via AJAX
  # Bloque id=2 pertenece al usuario 'a' (import.sql)
  Given path 'modulos/calendario'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/calendario/bloque/2'
  And header X-CSRF-TOKEN = csrf
  When method delete
  Then status 200
  And match response == { ok: true, message: '#null', data: '#null' }

Scenario: no se puede eliminar un bloque ajeno
  # Bloque id=1 pertenece al usuario 'b', el test está autenticado como 'a'
  Given path 'modulos/calendario'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/calendario/bloque/1'
  And header X-CSRF-TOKEN = csrf
  When method delete
  Then status 403

Scenario: detectar conflictos de un evento con bloques de miembros
  # Evento id=1 (Cena de bienvenida, 20:00-23:00) solapa con bloque id=1
  # (usuario 'b', Fútbol sala, 19:30-21:30)
  Given path 'modulos/calendario/conflictos/1'
  When method get
  Then status 200
  And match response == { ok: true, message: '#null', data: '#array' }
  And match response.data[0].usuario == 'b'
  And match response.data[0].tipo == 'ENTRENAMIENTO'

Scenario: crear evento recurrente semanal genera varias ocurrencias en el feed
  Given path 'modulos/calendario'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  # Crear evento recurrente semanal (a partir del 2026-07-07, lunes)
  Given path 'modulos/calendario/evento/crear'
  And form field titulo = 'Reunión semanal Karate'
  And form field fechaInicio = '2026-07-07T18:00'
  And form field fechaFin = '2026-07-07T19:00'
  And form field tipoRecurrencia = 'SEMANAL'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response.ok == true

  # El feed debe incluir varias ocurrencias en el rango
  Given path 'modulos/calendario/feed'
  And param start = '2026-07-01'
  And param end = '2026-10-31'
  When method get
  Then status 200
  And match response.ok == true
  And match response.data == '#array'
  # Verificar que hay al menos 2 entradas con el título recurrente
  * def reuniones = response.data.filter(e => e.title && e.title.includes('Reunión semanal Karate'))
  And assert reuniones.length >= 2

Scenario: crear bloque recurrente lun-vie y verificar aparece en feed
  Given path 'modulos/calendario'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/calendario/bloque/crear'
  And form field tipo = 'TRABAJO'
  And form field descripcion = 'Trabajo recurrente Karate'
  And form field inicio = '2026-07-06T09:00'
  And form field fin = '2026-07-06T17:00'
  And form field tipoRecurrencia = 'PERSONALIZADA'
  And form field diasSemana = '1,2,3,4,5'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response.ok == true

  # El feed debe expandir las ocurrencias dentro del rango
  Given path 'modulos/calendario/feed'
  And param start = '2026-07-06'
  And param end = '2026-07-12'
  When method get
  Then status 200
  And match response.ok == true
  * def trabajos = response.data.filter(e => e.extendedProps && e.extendedProps.descripcion === 'Trabajo recurrente Karate')
  And assert trabajos.length >= 3

Scenario: evento sin conflictos devuelve lista vacía
  # Creamos un evento en un horario sin bloques
  Given path 'modulos/calendario'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/calendario/evento/crear'
  And form field titulo = 'Evento sin conflicto'
  And form field descripcion = 'Test Karate'
  And form field fechaInicio = '2026-07-15T10:00'
  And form field fechaFin = '2026-07-15T11:00'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response.ok == true

  # Verificamos conflictos para el evento 1 (ya existe, horario conocido sin bloques en otro día)
  Given path 'modulos/calendario/conflictos/1024'
  When method get
  Then status 200
  And match response == { ok: true, message: '#null', data: '#array' }
  And match response.data == '#[]'

# ------------------------------------------------------------------
# Flujo de aprobación: escenarios 9-13 (estado acumulado)
# Evento(1) arranca PROPUESTO — ningún escenario anterior lo modifica
# ------------------------------------------------------------------

Scenario: aprobar un evento propuesto devuelve APROBADO
  Given path 'modulos/calendario'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/calendario/evento/1/aprobar'
  And header X-CSRF-TOKEN = csrf
  And request {}
  When method post
  Then status 200
  And match response == { ok: true, message: '#null', data: { estado: 'APROBADO' } }

Scenario: confirmar asistencia en evento aprobado
  # Evento(1) ya está APROBADO tras el escenario anterior
  Given path 'modulos/calendario'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/calendario/asistencia/1'
  And header X-CSRF-TOKEN = csrf
  And request {}
  When method post
  Then status 200
  And match response == { ok: true, message: '#null', data: { estado: '#string' } }

Scenario: no se puede aprobar un evento ya aprobado
  Given path 'modulos/calendario'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/calendario/evento/1/aprobar'
  And header X-CSRF-TOKEN = csrf
  And request {}
  When method post
  Then status 400
  And match response == { ok: false, message: '#string', data: '#null' }

Scenario: rechazar un evento aprobado lo pasa a RECHAZADO
  Given path 'modulos/calendario'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/calendario/evento/1/rechazar'
  And header X-CSRF-TOKEN = csrf
  And request {}
  When method post
  Then status 200
  And match response == { ok: true, message: '#null', data: { estado: 'RECHAZADO' } }

Scenario: no se puede confirmar asistencia en evento rechazado
  # Evento(1) está RECHAZADO tras el escenario anterior
  Given path 'modulos/calendario'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/calendario/asistencia/1'
  And header X-CSRF-TOKEN = csrf
  And request {}
  When method post
  Then status 400
  And match response == { ok: false, message: '#string', data: '#null' }
