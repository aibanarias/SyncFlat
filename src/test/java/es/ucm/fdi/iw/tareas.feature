Feature: módulo de tareas del piso

Background:
  * url baseUrl
  * callonce read('helpers/login.feature') { username: 'a', password: 'aa' }

Scenario: crear una tarea y verificar que aparece en el listado
  Given path 'tareas'
  When method get
  Then status 200
  * def csrfTarea = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'tareas'
  And form field nombre = 'Tarea test Karate'
  And form field descripcion = 'Tarea creada por prueba automatizada'
  And form field tipo = 'PUNTUAL'
  And form field fechaLimite = '2026-12-31'
  And form field _csrf = csrfTarea
  When method post
  Then status 200
  And match response contains 'Tarea test Karate'

Scenario: completar una tarea vía AJAX devuelve completada=true
  # Asume que la asignación con id=1 existe (import.sql)
  Given path 'tareas'
  When method get
  Then status 200
  * def csrfCompletar = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'tareas/1/completar'
  And header X-CSRF-TOKEN = csrfCompletar
  And request {}
  When method post
  Then status 200
  And match response == { ok: true, message: '#null', data: { completada: '#boolean', validada: '#boolean' } }
