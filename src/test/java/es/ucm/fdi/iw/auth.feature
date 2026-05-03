Feature: autenticación de usuarios

Background:
  * url baseUrl

Scenario: abrir login devuelve formulario con token CSRF
  Given path 'login'
  When method get
  Then status 200
  And match response contains '<form'
  And match response contains '_csrf'
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)
  * match csrf != null

Scenario: credenciales incorrectas redirigen a login con error
  Given path 'login'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)
  Given path 'login'
  And form field username = 'noexiste'
  And form field password = 'mal'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match responseHeaders['Location'] == '#notpresent'
  And match response contains 'login'

Scenario: login correcto como usuario regular (b)
  * callonce read('helpers/login.feature') { username: 'b', password: 'aa' }
  Given path 'modulos/home'
  When method get
  Then status 200

Scenario: login correcto como administrador (a)
  * callonce read('helpers/login.feature') { username: 'a', password: 'aa' }
  Given path 'admin/'
  When method get
  Then status 200
  And match response contains 'Administración'
