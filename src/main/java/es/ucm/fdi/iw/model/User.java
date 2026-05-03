package es.ucm.fdi.iw.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Usuario de la aplicación.
 * <p>
 * Los roles se almacenan como una cadena separada por comas (p. ej. {@code "USER,ADMIN"})
 * para simplificar la persistencia. La tabla se llama {@code IWUser} para evitar
 * conflictos con la palabra reservada {@code USER} en H2.
 * <p>
 * Implementa {@link Transferable} para serializar a JSON sin exponer el hash de la contraseña.
 * <p>
 * Se usa {@code @Getter}/{@code @Setter} en lugar de {@code @Data} para evitar que Lombok
 * genere {@code equals()}/{@code hashCode()} sobre las colecciones lazy ({@code sent},
 * {@code received}, {@code groups}), lo que causaría {@code LazyInitializationException}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@NamedQueries({
    @NamedQuery(name = "User.byUsername", query = "SELECT u FROM User u "
        + "WHERE u.username = :username AND u.enabled = TRUE"),
    @NamedQuery(name = "User.hasUsername", query = "SELECT COUNT(u) "
        + "FROM User u "
        + "WHERE u.username = :username"),
    @NamedQuery(name = "User.topics", query = "SELECT t.key "
        + "FROM Topic t JOIN t.members u "
        + "WHERE u.id = :id")
})
@Table(name = "IWUser")
public class User implements Transferable<User.Transfer> {

  public enum Role {
    USER,
    ADMIN,
  }

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
  @SequenceGenerator(name = "gen", sequenceName = "gen")
  private long id;

  @Column(nullable = false, unique = true)
  private String username;
  @Column(nullable = false)
  private String password;

  private String firstName;
  private String lastName;

  private boolean enabled;
  private String roles; // roles separados por coma, ej: "USER" o "USER,ADMIN"

  @OneToMany
  @JoinColumn(name = "sender_id")
  private List<Message> sent = new ArrayList<>();
  @OneToMany
  @JoinColumn(name = "recipient_id")
  private List<Message> received = new ArrayList<>();
  @ManyToMany(mappedBy = "members")
  private List<Topic> groups = new ArrayList<>();

  /**
   * Comprueba si el usuario tiene el rol indicado.
   *
   * @param role rol a comprobar
   * @return {@code true} si el rol está presente en la cadena de roles del usuario
   */
  public boolean hasRole(Role role) {
    String roleName = role.name();
    return roles != null && Arrays.asList(roles.split(",")).contains(roleName);
  }

  @Getter
  @AllArgsConstructor
  public static class Transfer {
    private long id;
    private String username;
    private int totalReceived;
    private int totalSent;
    private String groups;
  }

  @Override
  public Transfer toTransfer() {
    StringBuilder gs = new StringBuilder();
    for (Topic g : groups) {
      gs.append(g.getName()).append(", ");
    }
    return new Transfer(id, username, received.size(), sent.size(), gs.toString());
  }

  @Override
  public String toString() {
    return toTransfer().toString();
  }
}
