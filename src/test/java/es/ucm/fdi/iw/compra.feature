Feature: módulo de lista de la compra

Background:
  * url baseUrl
  * callonce read('helpers/login.feature') { username: 'a', password: 'aa' }

Scenario: usuario autenticado puede ver la lista de la compra
  Given path 'modulos/compra'
  When method get
  Then status 200
  And match response contains 'Lista de la compra'

Scenario: toggle de item de compra devuelve estado actualizado
  # Asume que el item con id=1 existe (import.sql)
  Given path 'modulos/compra'
  When method get
  Then status 200
  * def csrfToggle = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/compra/item/1/toggle'
  And header X-CSRF-TOKEN = csrfToggle
  And request {}
  When method post
  Then status 200
  And match response == { ok: true, message: '#null', data: { comprado: '#boolean' } }

# ------------------------------------------------------------------
# 3. La vista de gestión permite a cualquier miembro crear listas
# ------------------------------------------------------------------
Scenario: la vista de gestión es accesible y muestra formulario de lista
  Given path 'modulos/compra/gestion'
  When method get
  Then status 200
  And match response contains 'Nueva lista de la compra'
  And match response contains 'Participantes'
  # Ya no debe mostrar el aviso de "solo el administrador"
  And match response !contains 'Solo el administrador puede crear'

# ------------------------------------------------------------------
# 4. Cualquier miembro puede crear una lista con participantes específicos
# ------------------------------------------------------------------
Scenario: crear lista con participantes concretos
  Given path 'modulos/compra/gestion'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/compra/lista'
  And form field nombre = 'Lista Karate Participantes'
  And form field participanteIds = '1'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'Lista Karate Participantes'
