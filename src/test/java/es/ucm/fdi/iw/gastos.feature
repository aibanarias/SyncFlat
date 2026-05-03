Feature: módulo de gastos compartidos

Background:
  * url baseUrl
  * callonce read('helpers/login.feature') { username: 'a', password: 'aa' }

Scenario: usuario autenticado puede ver el listado de gastos
  Given path 'modulos/gastos'
  When method get
  Then status 200
  And match response contains 'Gastos'

Scenario: crear un gasto y verificar que aparece en el listado
  Given path 'modulos/gastos'
  When method get
  Then status 200
  * def csrfGasto = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/gastos'
  And form field concepto = 'Gasto test Karate'
  And form field importe = '60.00'
  And form field _csrf = csrfGasto
  When method post
  Then status 200
  And match response contains 'Gasto test Karate'

Scenario: el pagador no puede pagarse su propia participación
  # pg(id=1): usuario='a', gasto(id=1) donde pagador='a' → pagado=TRUE desde import.sql
  # Invariante: el pagador nace saldado y no puede volver a pagarse.
  # La API debe rechazarlo con 400 (ya pagado o auto-pago bloqueado).
  Given path 'modulos/gastos'
  When method get
  Then status 200
  * def csrfPago = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/gastos/participante/1/pagar'
  And header X-CSRF-TOKEN = csrfPago
  And request {}
  When method post
  Then status 400
  And match response == { ok: false, message: '#string', data: '#null' }

# ------------------------------------------------------------------
# 4. La sección de balance muestra deudas netas pendientes
# ------------------------------------------------------------------
Scenario: la sección de balance refleja deudas netas pendientes entre compañeros
  # pg1 (a/gasto1) ya está pagado desde import.sql (pagador = usuario, invariante).
  # pg2 (b/gasto1, 22.50€) sigue pendiente → b debe dinero a a.
  # El gasto "Gasto test Karate" (60€, pagador=a) añade otra deuda de b hacia a.
  # El balance debe listar a 'b' como deudor de 'a'.
  Given path 'modulos/gastos'
  When method get
  Then status 200
  And match response contains 'Balance entre compañeros'
  And match response contains '@b'
  And match response contains '@a'

# ------------------------------------------------------------------
# 5. La sección "Mis operaciones" es visible en la vista
# ------------------------------------------------------------------
Scenario: la sección mis operaciones pendientes aparece en la vista
  Given path 'modulos/gastos'
  When method get
  Then status 200
  And match response contains 'Mis operaciones pendientes'
  # 'a' es el pagador del gasto 1 y de 'Gasto test Karate' → tiene pendiente de recibir de 'b'
  And match response contains 'Pendiente de recibir'

# ------------------------------------------------------------------
# 6. El historial muestra el detalle de cada gasto
# ------------------------------------------------------------------
Scenario: el historial de gastos incluye detalle por gasto
  Given path 'modulos/gastos'
  When method get
  Then status 200
  And match response contains 'Historial de gastos'
  And match response contains 'Factura de internet'
  And match response contains 'Gasto test Karate'
