Feature: SyncFlat - smoke test del panel principal

# Verifica que el dashboard de home carga correctamente.
# El resto de módulos tienen sus propios smoke tests integrados
# en sus respectivos escenarios funcionales.

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
