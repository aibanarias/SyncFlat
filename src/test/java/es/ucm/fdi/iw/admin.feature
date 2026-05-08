Feature: panel de administración

Background:
  * url baseUrl

Scenario: administrador puede acceder al panel
  * callonce read('helpers/login.feature') { username: 'a', password: 'aa' }
  Given path 'admin/'
  When method get
  Then status 200
  And match response contains 'Usuarios'

Scenario: usuario sin rol ADMIN recibe 403
  * callonce read('helpers/login.feature') { username: 'b', password: 'aa' }
  Given path 'admin/'
  When method get
  Then status 403

