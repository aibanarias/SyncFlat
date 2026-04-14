package es.ucm.fdi.iw;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.ucm.fdi.iw.model.Topic;
import es.ucm.fdi.iw.model.User;

/**
 * Manejador que se ejecuta tras un login exitoso.
 * <p>
 * Carga el usuario desde la base de datos, almacena sus datos en sesión
 * (atributo {@code u}) y calcula las URLs base para HTTP y WebSocket.
 * El usuario se guarda en sesión al autenticarse y no se refresca
 * automáticamente en cada petición: si cambian datos relevantes del perfil,
 * hay que actualizarlo manualmente en sesión.
 */
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

  @Autowired
  private HttpSession session;

  @Autowired
  private EntityManager entityManager;

  private static Logger log = LogManager.getLogger(LoginSuccessHandler.class);

  /**
   * Inicializa la sesión del usuario recién autenticado.
   * <p>
   * Almacena el objeto {@link User} en el atributo de sesión {@code u},
   * calcula la URL base y la URL de WebSocket (con {@code wss://} en el
   * entorno UCM), y guarda los tópicos suscritos. Redirige a {@code admin/}
   * o a {@code user/{id}} según el rol.
   */
  @Override
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) throws IOException, ServletException {

    addSameSiteCookieAttribute(response);

    String username = ((org.springframework.security.core.userdetails.User) authentication.getPrincipal())
        .getUsername();

    log.info("Storing user info for {} in session {}", username, session.getId());
    User u = entityManager.createNamedQuery("User.byUsername", User.class)
        .setParameter("username", username)
        .getSingleResult();
    session.setAttribute("u", u);

    // Calcula URL base y WebSocket eliminando el protocolo (ej. //host:puerto/ctx/)
    // En el entorno UCM se usa wss:// en lugar de ws://
    String url = request.getRequestURL().toString()
        .replaceFirst("/[^/]*$", "")
        .replaceFirst("[^/]*", "");
    String ws = "ws:" + url + "/ws";
    if (url.contains("ucm.es")) {
      ws = ws.replace("ws:", "wss:");
    }
    session.setAttribute("url", url);
    session.setAttribute("ws", ws);

    List<String> topics = entityManager.createNamedQuery("User.topics", String.class)
        .setParameter("id", u.getId())
        .getResultList();
    session.setAttribute("topics", String.join(",", topics));

    String nextUrl = u.hasRole(User.Role.ADMIN) ? "admin/" : "modulos/home";

    log.info("LOG IN: {} (id {}) -- session is {}, websocket is {} -- redirected to {}",
        u.getUsername(), u.getId(), session.getId(), ws, nextUrl);

    response.sendRedirect(nextUrl);
  }

  /**
   * Añade el atributo {@code SameSite=Strict} a todas las cabeceras
   * {@code Set-Cookie} de la respuesta para evitar que el navegador rechace
   * la cookie de sesión en contextos de terceros.
   *
   * @param response respuesta HTTP sobre la que se modifican las cabeceras
   */
  private void addSameSiteCookieAttribute(HttpServletResponse response) {
    Collection<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
    boolean firstHeader = true;
    // there can be multiple Set-Cookie attributes
    for (String header : headers) {
      if (firstHeader) {
        response.setHeader(HttpHeaders.SET_COOKIE,
            String.format("%s; %s", header, "SameSite=Strict"));
        firstHeader = false;
        continue;
      }
      response.addHeader(HttpHeaders.SET_COOKIE,
          String.format("%s; %s", header, "SameSite=Strict"));
    }
  }
}
