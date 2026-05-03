package es.ucm.fdi.iw.controller;

import es.ucm.fdi.iw.LocalData;
import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.Transferable;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.model.User.Role;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Controlador de gestión de usuarios.
 * <p>
 * Cubre el perfil de usuario (consulta, edición, foto), el envío de mensajes
 * directos mediante WebSocket y la consulta de mensajes recibidos.
 * El acceso a {@code /user/**} está restringido al rol USER por configuración.
 */
@Controller()
@RequestMapping("user")
public class UserController extends BaseController {

  private static final Logger log = LogManager.getLogger(UserController.class);

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private LocalData localData;

  @Autowired
  private SimpMessagingTemplate messagingTemplate;

  @Autowired
  private PasswordEncoder passwordEncoder;

  /**
   * Se lanza cuando un usuario intenta modificar un perfil que no es el suyo
   * y tampoco tiene rol ADMIN. Devuelve HTTP 403.
   */
  @ResponseStatus(value = HttpStatus.FORBIDDEN, reason = "No eres administrador, y éste no es tu perfil")
  public static class NoEsTuPerfilException extends RuntimeException {
  }

  /**
   * Codifica una contraseña en claro usando BCrypt con sal aleatoria.
   * Cada llamada produce un hash diferente aunque la contraseña sea la misma.
   *
   * @param rawPassword contraseña en claro
   * @return hash almacenable (prefijado con el identificador del algoritmo)
   */
  public String encodePassword(String rawPassword) {
    return passwordEncoder.encode(rawPassword);
  }

  /**
   * Genera un token aleatorio criptográficamente seguro codificado en Base64 URL-safe.
   *
   * @param byteLength número de bytes de entropía
   * @return cadena Base64 sin relleno
   */
  public static String generateRandomBase64Token(int byteLength) {
    SecureRandom secureRandom = new SecureRandom();
    byte[] token = new byte[byteLength];
    secureRandom.nextBytes(token);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
  }

  /** Vista del perfil de usuario. */
  @GetMapping("{id}")
  public String index(@PathVariable long id, Model model, HttpSession session) {
    User target = entityManager.find(User.class, id);
    model.addAttribute("user", target);
    return "user";
  }

  /**
   * Crea o actualiza un usuario.
   * <p>
   * Con {@code id == -1} y rol ADMIN se crea un usuario nuevo con contraseña aleatoria.
   * En cualquier otro caso se actualiza el usuario existente. Un usuario solo puede
   * editar su propio perfil; los administradores pueden editar cualquiera.
   * Si se cambia la contraseña y las dos copias no coinciden, se devuelve 400.
   *
   * @param id identificador del usuario a modificar, o {@code -1} para crear uno nuevo
   */
  @PostMapping("/{id}")
  @Transactional
  public String postUser(
      HttpServletResponse response,
      @PathVariable long id,
      @ModelAttribute User edited,
      @RequestParam(required = false) String pass2,
      Model model, HttpSession session) throws IOException {

    User requester = (User) session.getAttribute("u");
    User target = null;
    if (id == -1 && requester.hasRole(Role.ADMIN)) {
      target = new User();
      target.setPassword(encodePassword(generateRandomBase64Token(12)));
      target.setEnabled(true);
      entityManager.persist(target);
      entityManager.flush();
      id = target.getId();
    }

    target = entityManager.find(User.class, id);
    model.addAttribute("user", target);

    if (requester.getId() != target.getId() &&
        !requester.hasRole(Role.ADMIN)) {
      throw new NoEsTuPerfilException();
    }

    if (edited.getPassword() != null) {
      if (!edited.getPassword().equals(pass2)) {
        log.warn("Passwords do not match - returning to user form");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("user", target);
        return "user";
      } else {
        target.setPassword(encodePassword(edited.getPassword()));
      }
    }
    target.setUsername(edited.getUsername());
    target.setFirstName(edited.getFirstName());
    target.setLastName(edited.getLastName());

    // Si el usuario edita su propio perfil, actualizar también el objeto en sesión
    if (requester.getId() == target.getId()) {
      session.setAttribute("u", target);
    }

    return "user";
  }

  /** Devuelve el stream de la imagen de perfil por defecto desde el classpath. */
  private static InputStream defaultPic() {
    return new BufferedInputStream(Objects.requireNonNull(
        UserController.class.getClassLoader().getResourceAsStream(
            "static/img/default-pic.jpg")));
  }

  /**
   * Descarga la foto de perfil de un usuario. Si no tiene foto subida,
   * devuelve la imagen por defecto.
   *
   * @param id identificador del usuario
   */
  @GetMapping("{id}/pic")
  public StreamingResponseBody getPic(@PathVariable long id) throws IOException {
    File f = localData.getFile("user", "" + id + ".jpg");
    InputStream in = new BufferedInputStream(f.exists() ? new FileInputStream(f) : UserController.defaultPic());
    return os -> FileCopyUtils.copy(in, os);
  }

  /**
   * Sube una nueva foto de perfil para el usuario indicado.
   * Solo el propio usuario o un administrador pueden subir la foto.
   *
   * @param id identificador del usuario
   */
  @PostMapping("{id}/pic")
  @ResponseBody
  public String setPic(@RequestParam("photo") MultipartFile photo, @PathVariable long id,
      HttpServletResponse response, HttpSession session, Model model) throws IOException {

    User target = entityManager.find(User.class, id);
    model.addAttribute("user", target);

    User requester = (User) session.getAttribute("u");
    if (requester.getId() != target.getId() &&
        !requester.hasRole(Role.ADMIN)) {
      throw new NoEsTuPerfilException();
    }

    log.info("Updating photo for user {}", id);
    File f = localData.getFile("user", "" + id + ".jpg");
    if (photo.isEmpty()) {
      log.info("failed to upload photo: emtpy file?");
    } else {
      try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(f))) {
        byte[] bytes = photo.getBytes();
        stream.write(bytes);
        log.info("Uploaded photo for {} into {}!", id, f.getAbsolutePath());
      } catch (Exception e) {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        log.warn("Error uploading " + id + " ", e);
      }
    }
    return "{\"status\":\"photo uploaded correctly\"}";
  }

  @GetMapping("error")
  public String error(Model model, HttpSession session, HttpServletRequest request) {
    model.addAttribute("sess", session);
    model.addAttribute("req", request);
    return "error";
  }

  /** Devuelve la lista de mensajes recibidos por el usuario en sesión. */
  @GetMapping(path = "received", produces = "application/json")
  @Transactional
  @ResponseBody
  public List<Message.Transfer> retrieveMessages(HttpSession session) {
    long userId = ((User) session.getAttribute("u")).getId();
    User u = entityManager.find(User.class, userId);
    log.info("Generating message list for user {} ({} messages)",
        u.getUsername(), u.getReceived().size());
    return u.getReceived().stream().map(Transferable::toTransfer).collect(Collectors.toList());
  }

  /** Devuelve el número de mensajes no leídos y lo actualiza en sesión. */
  @GetMapping(path = "unread", produces = "application/json")
  @ResponseBody
  public String checkUnread(HttpSession session) {
    long userId = ((User) session.getAttribute("u")).getId();
    long unread = entityManager.createNamedQuery("Message.countUnread", Long.class)
        .setParameter("userId", userId)
        .getSingleResult();
    session.setAttribute("unread", unread);
    return "{\"unread\": " + unread + "}";
  }

  /**
   * Envía un mensaje directo a un usuario, lo persiste en base de datos
   * y lo reenvía por WebSocket al destinatario mediante su cola personal.
   *
   * @param id identificador del usuario destinatario
   * @param o  JSON con el campo {@code message}
   * @throws JsonProcessingException si la serialización falla
   */
  @PostMapping("/{id}/msg")
  @ResponseBody
  @Transactional
  public String postMsg(@PathVariable long id,
      @RequestBody JsonNode o, Model model, HttpSession session)
      throws JsonProcessingException {

    String text = o.get("message").asText();
    User u = entityManager.find(User.class, id);
    User sender = entityManager.find(
        User.class, ((User) session.getAttribute("u")).getId());
    model.addAttribute("user", u);

    Message m = new Message();
    m.setRecipient(u);
    m.setSender(sender);
    m.setDateSent(LocalDateTime.now());
    m.setText(text);
    entityManager.persist(m);
    entityManager.flush();

    String json = new ObjectMapper().writeValueAsString(m.toTransfer());

    log.info("Sending a message to {} with contents '{}'", id, json);

    messagingTemplate.convertAndSend("/user/" + u.getUsername() + "/queue/updates", json);
    return "{\"result\": \"message sent.\"}";
  }
}
