@ignore
Feature: helper de autenticación reutilizable

# Uso: * def login = callonce read('helpers/login.feature') { username: 'a', password: 'aa' }
# Requiere que 'url' esté configurada en el Background del feature que lo llama.

Scenario: login
  * url baseUrl
  Given path 'login'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)
  Given path 'login'
  And form field username = username
  And form field password = password
  And form field _csrf = csrf
  When method post
  Then status 200
