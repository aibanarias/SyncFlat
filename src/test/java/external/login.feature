Feature: autenticación mediante navegador

  Scenario: credenciales incorrectas muestran error en la página
    Given driver baseUrl + '/login'
    And input('#username', 'dummy')
    And input('#password', 'world')
    When submit().click(".form-signin button")
    Then match html('.error') contains 'Error en nombre de usuario o contraseña'

  @login_b
  Scenario: login correcto como miembro redirige a home
    Given driver baseUrl + '/login'
    And input('#username', 'b')
    And input('#password', 'aa')
    When submit().click(".form-signin button")
    Then waitForUrl(baseUrl + '/home')

  @login_a
  Scenario: login correcto como admin redirige al panel de administración
    Given driver baseUrl + '/login'
    And input('#username', 'a')
    And input('#password', 'aa')
    When submit().click(".form-signin button")
    Then waitForUrl(baseUrl + '/admin/')

  Scenario: logout después de login deja la sesión cerrada
    Given driver baseUrl + '/login'
    And input('#username', 'a')
    And input('#password', 'aa')
    When submit().click(".form-signin button")
    Then waitForUrl(baseUrl + '/admin/')
    When click("{button}logout")
    Then waitFor('#username')
