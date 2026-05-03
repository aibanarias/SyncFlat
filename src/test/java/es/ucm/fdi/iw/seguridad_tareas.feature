Feature: blindaje multi-piso en el módulo de tareas

# Estado inicial garantizado por import.sql:
#   - AsignacionTarea(1): tarea 'Fregar el suelo', asignada a 'b', pertenece a Piso 1
#   - Usuario 'd' (id=4): pertenece SOLO a Piso 2 (Piso Retiro)
#
# La asignación 1 empieza como PENDIENTE. El guard de piso en TareaService
# se ejecuta ANTES de cualquier comprobación de estado, por lo que ambos
# escenarios producen 403 independientemente del estado de la asignación.
#
# Este feature corre antes de tareas.feature ('s' < 't'), de modo que la
# asignación 1 todavía no ha sido modificada por ningún test anterior.

Background:
  * url baseUrl
  * callonce read('helpers/login.feature') { username: 'd', password: 'aa' }

# ------------------------------------------------------------------
# 1. Usuario de otro piso no puede completar una asignación ajena
# ------------------------------------------------------------------
Scenario: usuario de Piso 2 no puede completar asignación de Piso 1
  # 'd' pertenece a Piso 2; la asignacion id=1 pertenece a Piso 1 → 403
  Given path 'modulos/tareas'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/tareas/1/completar'
  And header X-CSRF-TOKEN = csrf
  And request {}
  When method post
  Then status 403
  And match response == { ok: false, message: '#string', data: '#null' }

# ------------------------------------------------------------------
# 2. Usuario de otro piso no puede validar una asignación ajena
# ------------------------------------------------------------------
Scenario: usuario de Piso 2 no puede validar asignación de Piso 1
  # El guard de piso se ejecuta antes de la comprobación de estado (PENDIENTE),
  # por lo que la respuesta es 403 y no 400 por 'debe completarse antes'.
  Given path 'modulos/tareas'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/tareas/1/validar'
  And header X-CSRF-TOKEN = csrf
  And request {}
  When method post
  Then status 403
  And match response == { ok: false, message: '#string', data: '#null' }
