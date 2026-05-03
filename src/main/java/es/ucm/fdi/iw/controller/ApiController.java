package es.ucm.fdi.iw.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.ucm.fdi.iw.model.Topic;
import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.model.User.Role;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

/**
 * API REST de la aplicación.
 * <p>
 * Endpoints de uso general: mensajería STOMP, consultas de estado y
 * ejecución de scripts JS con karate-js. El prefijo {@code /api/**} es
 * público por configuración, por lo que cada endpoint debe protegerse
 * individualmente si lo requiere.
 */
@RestController
@RequestMapping("api")
public class ApiController {

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private SimpMessagingTemplate messagingTemplate;

  private static final Logger log = LogManager.getLogger(ApiController.class);

  /**
   * Publica un mensaje en un tópico STOMP y lo persiste en base de datos.
   * Solo pueden enviar mensajes los miembros del tópico o los administradores.
   *
   * @param name clave del tópico destino
   * @param o    cuerpo JSON con el campo {@code message}
   * @throws JsonProcessingException si la serialización JSON falla
   */
  @PostMapping("/topic/{name}")
  @ResponseBody
  @Transactional
  public Map<String,String> postMsg(@PathVariable String name,
      @RequestBody JsonNode o, Model model, HttpSession session,
      HttpServletResponse response)
      throws JsonProcessingException {

    String text = o.get("message").asText();
    User sender = entityManager.find(
        User.class, ((User) session.getAttribute("u")).getId());
    Topic target = entityManager.createNamedQuery("Topic.byKey", Topic.class)
        .setParameter("key", name).getSingleResult();

    if (! sender.hasRole(Role.ADMIN) && ! target.getMembers().contains(sender)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return Map.of("error", "user not in group");
    }

    Message m = new Message();
    m.setRecipient(null);
    m.setSender(sender);
    m.setTopic(target);
    m.setDateSent(LocalDateTime.now());
    m.setText(text);
    entityManager.persist(m);
    entityManager.flush();

    String json = new ObjectMapper().writeValueAsString(m.toTransfer());
    log.info("Sending a message to  group {} with contents '{}'", target.getName(), json);
    messagingTemplate.convertAndSend("/topic/" + name, json);
    return Map.of("result", "message sent");
  }

  /**
   * Devuelve todos los mensajes de un tópico en formato JSON.
   * Solo accesible para miembros del tópico o administradores.
   *
   * @param name clave del tópico
   * @throws JsonProcessingException si la serialización JSON falla
   */
  @GetMapping("/topic/{name}")
  @ResponseBody
  @Transactional
  public Map<String,String> getMessages(@PathVariable String name, HttpSession session,
        HttpServletResponse response)
      throws JsonProcessingException {

      User requester = entityManager.find(
          User.class, ((User) session.getAttribute("u")).getId());
      Topic target = entityManager.createNamedQuery("Topic.byKey", Topic.class)
          .setParameter("key", name).getSingleResult();

      if (! requester.hasRole(Role.ADMIN) && ! target.getMembers().contains(requester)) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return Map.of("error", "user not in group");
      }
      return Map.of("messages", new ObjectMapper().writeValueAsString(
        target.getMessages().stream()
          .map(Message::toTransfer).toArray()
      ));
  }
}
