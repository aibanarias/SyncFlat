Feature: SyncFlat - smoke tests de integración

# Recorre los flujos principales de la aplicación.
# Los tests de módulo en detalle están en auth/gastos/tareas/compra/admin.feature

Background:
  * url baseUrl
  * callonce read('helpers/login.feature') { username: 'a', password: 'aa' }

Scenario: home muestra resumen del piso
  Given path 'modulos/home'
  When method get
  Then status 200
  And match response contains 'eventos'
  And match response contains 'tareas'
  And match response contains 'Alertas'

Scenario: listado de gastos accesible
  Given path 'modulos/gastos'
  When method get
  Then status 200
  And match response contains 'Gastos'

Scenario: lista de compra accesible
  Given path 'modulos/compra'
  When method get
  Then status 200
  And match response contains 'Lista de la compra'

Scenario: tareas accesibles
  Given path 'modulos/tareas'
  When method get
  Then status 200
  And match response contains 'Tareas'

Scenario: crear gasto y verificar persistencia
  Given path 'modulos/gastos'
  When method get
  Then status 200
  * def csrfGasto = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/gastos'
  And form field concepto = 'Gasto smoke test'
  And form field importe = '30.00'
  And form field _csrf = csrfGasto
  When method post
  Then status 200
  And match response contains 'Gasto smoke test'

Scenario: crear tarea y verificar persistencia
  Given path 'modulos/tareas'
  When method get
  Then status 200
  * def csrfTarea = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/tareas'
  And form field nombre = 'Tarea smoke test'
  And form field descripcion = 'Tarea creada por smoke test'
  And form field tipo = 'PUNTUAL'
  And form field fechaLimite = '2026-12-31'
  And form field _csrf = csrfTarea
  When method post
  Then status 200
  And match response contains 'Tarea smoke test'
