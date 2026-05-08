Feature: gestión de alertas del piso

# Estado inicial (import.sql):
#   Alerta(1): 'Revision del gas programada para el lunes', tipo=INFO, leida=FALSE, piso=1
#
# Secuencia de escenarios (orden acumulado, 'alertas' < 'auth' < 'calendario' ...):
#   1. Home muestra la alerta no leída del seed
#   2. Marcar alerta 1 como leída vía AJAX → ok=true
#   3. Marcar todas las restantes → flash con 'marcadas'

Background:
  * url baseUrl
  * callonce read('helpers/login.feature') { username: 'a', password: 'aa' }

# ------------------------------------------------------------------
# 1. Home muestra alertas no leídas
# ------------------------------------------------------------------
Scenario: home muestra la sección de alertas
  Given path 'modulos/home'
  When method get
  Then status 200
  And match response contains 'Alertas'

# ------------------------------------------------------------------
# 2. Marcar una alerta como leída devuelve ok=true
# ------------------------------------------------------------------
Scenario: marcar una alerta como leída vía AJAX
  Given path 'modulos/home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/alertas/1/leer'
  And header X-CSRF-TOKEN = csrf
  And request {}
  When method post
  Then status 200
  And match response == { ok: true, message: '#null', data: { leida: true } }

# ------------------------------------------------------------------
# 3. No se puede volver a marcar una alerta ya leída
# ------------------------------------------------------------------
Scenario: marcar dos veces la misma alerta devuelve error
  Given path 'modulos/home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/alertas/1/leer'
  And header X-CSRF-TOKEN = csrf
  And request {}
  When method post
  Then status 400
  And match response == { ok: false, message: '#string', data: '#null' }

# ------------------------------------------------------------------
# 4. Marcar todas las alertas pendientes
# ------------------------------------------------------------------
Scenario: marcar todas las alertas como leídas
  Given path 'modulos/home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/alertas/leer-todas'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'marcadas'
