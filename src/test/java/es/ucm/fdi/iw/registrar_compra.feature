Feature: registro de compra realizada desde una lista activa

# Estado inicial garantizado por import.sql:
#   - Lista_Compra(1): 'Compra semana 42', completada=FALSE, piso=1
#   - Lista_Compra(2): 'Lista supermercado', completada=FALSE, piso=1
#   - Compra(1) en import.sql es dato histórico; lista(1) sigue activa
#
# Secuencia de escenarios (estado acumulado, orden alfabético 'r' < 't'):
#   1. Registrar lista 1 sin gasto → lista 1 queda completada
#   2. Registrar lista 2 con gasto → lista 2 queda completada + Gasto creado
#   3. Intentar registrar lista 1 de nuevo → error (ya completada)

Background:
  * url baseUrl
  * callonce read('helpers/login.feature') { username: 'a', password: 'aa' }

# ------------------------------------------------------------------
# 1. Registrar una compra sin gasto asociado
# ------------------------------------------------------------------
Scenario: registrar compra en lista activa sin gasto
  Given path 'compra'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'compra/registrar'
  And form field listaId = '1'
  And form field importeTotal = '18.40'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'Compra registrada correctamente'

# ------------------------------------------------------------------
# 2. Registrar una compra con gasto compartido generado automáticamente
# ------------------------------------------------------------------
Scenario: registrar compra en lista activa con gasto compartido
  Given path 'compra'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'compra/registrar'
  And form field listaId = '2'
  And form field importeTotal = '42.00'
  And form field crearGasto = 'true'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'Compra registrada correctamente'
  And match response contains 'gasto compartido'

# ------------------------------------------------------------------
# 3. No se puede registrar una lista ya completada
# ------------------------------------------------------------------
Scenario: error al registrar compra en lista ya completada
  # lista 1 quedó completada en el escenario 1
  Given path 'compra'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'compra/registrar'
  And form field listaId = '1'
  And form field importeTotal = '10.00'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'ya fue registrada'

# ------------------------------------------------------------------
# 4. No se puede hacer toggle de un ítem en una lista ya cerrada
#    (el historial queda congelado en el estado del momento del cierre)
# ------------------------------------------------------------------
Scenario: toggle en lista cerrada devuelve error 400
  # item 1 pertenece a lista 1, que quedó completada en el escenario 1
  Given path 'compra'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'compra/item/1/toggle'
  And header X-CSRF-TOKEN = csrf
  And request {}
  When method post
  Then status 400
  And match response == { ok: false, message: '#string', data: '#null' }
