Feature: flujo de validación de tareas del piso

# Estado inicial esperado (acumulado de tareas.feature que corre antes, 't' < 'v'):
#   - asignacion id=1 (tarea "Fregar el suelo", asignada a 'b' / user_id=2)
#     está en estado COMPLETADA: fechaCompletada != NULL, validada = false
#
# Cada escenario hace su propio login para controlar el usuario activo.
# La secuencia es importante: 1 (no cambia estado) → 2 (valida) → 3 y 4 (post-validación).

Background:
  * url baseUrl

# ------------------------------------------------------------------
# 1. El asignado no puede validar su propia tarea (autovalidación prohibida)
# ------------------------------------------------------------------
Scenario: el usuario asignado no puede validar su propia tarea
  # 'b' (user_id=2) es el asignado de asignacion id=1 → autovalidación prohibida
  * call read('helpers/login.feature') { username: 'b', password: 'aa' }
  Given path 'tareas'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'tareas/1/validar'
  And header X-CSRF-TOKEN = csrf
  And request {}
  When method post
  Then status 400
  And match response == { ok: false, message: '#string', data: '#null' }
  And match response.message contains 'tú mismo'

# ------------------------------------------------------------------
# 2. Otro miembro puede validar la tarea completada
# ------------------------------------------------------------------
Scenario: otro miembro puede validar la tarea completada por el asignado
  # 'a' (user_id=1) no es el asignado de asignacion id=1 → puede validar
  * call read('helpers/login.feature') { username: 'a', password: 'aa' }
  Given path 'tareas'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'tareas/1/validar'
  And header X-CSRF-TOKEN = csrf
  And request {}
  When method post
  Then status 200
  And match response == { ok: true, message: '#null', data: { validada: true, validador: 'a', nuevaOcurrencia: true } }

# ------------------------------------------------------------------
# 3. Una tarea validada no puede reabrirse
# ------------------------------------------------------------------
Scenario: no se puede reabrir una tarea ya validada
  # asignacion id=1 está VALIDADA tras el escenario anterior
  * call read('helpers/login.feature') { username: 'a', password: 'aa' }
  Given path 'tareas'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'tareas/1/completar'
  And header X-CSRF-TOKEN = csrf
  And request {}
  When method post
  Then status 400
  And match response == { ok: false, message: '#string', data: '#null' }
  And match response.message contains 'validada'

# ------------------------------------------------------------------
# 4. No se puede validar una tarea que ya fue validada
# ------------------------------------------------------------------
Scenario: no se puede volver a validar una tarea ya validada
  # asignacion id=1 sigue VALIDADA
  * call read('helpers/login.feature') { username: 'b', password: 'aa' }
  Given path 'tareas'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'tareas/1/validar'
  And header X-CSRF-TOKEN = csrf
  And request {}
  When method post
  Then status 400
  And match response == { ok: false, message: '#string', data: '#null' }
  And match response.message contains 'ya está validada'
