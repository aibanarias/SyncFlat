Feature: recurrencia de tareas — generación de siguiente ocurrencia

# Depende de validacion_tareas.feature (corre antes: 'validacion' < 'vr').
# Tras validar asignacion id=1 (tarea 'Fregar el suelo', RECURRENTE SEMANAL,
# fechaLimite=2025-10-19), TareaService debe haber creado una nueva asignación
# con la misma tarea copiada y fechaLimite=2025-10-26.

Background:
  * url baseUrl
  * callonce read('helpers/login.feature') { username: 'a', password: 'aa' }

Scenario: la siguiente ocurrencia aparece en la lista de tareas pendientes
  Given path 'modulos/tareas'
  When method get
  Then status 200
  And match response contains '2025-10-26'
  And match response contains 'Fregar el suelo'
