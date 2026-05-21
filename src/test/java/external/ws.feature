Feature: envío de mensajes entre usuarios

  Scenario: mensaje enviado por 'a' llega a la bandeja de 'b'
    # 'a' inicia sesión y navega al perfil de 'b' para enviarle un mensaje
    Given call read('login.feature@login_a')
    And driver baseUrl + '/user/2'
    And def mensaje = script("'el número secreto es el ' + Math.floor(Math.random() * 1000)")
    And input('#message', mensaje)
    And click("button[id=sendmsg]")
    # esperar a que el AJAX de envío complete
    And delay(500)

    # 'b' inicia sesión y comprueba que el mensaje ha llegado
    And call read('login.feature@login_b')
    And driver baseUrl + '/user/2'
    # los mensajes se cargan via AJAX desde /user/received al montar la página
    And delay(500)
    Then match html('#mensajes') contains mensaje
    And driver.screenshot()
