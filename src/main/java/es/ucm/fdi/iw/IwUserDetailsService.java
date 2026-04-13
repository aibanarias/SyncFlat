package es.ucm.fdi.iw;

import java.util.ArrayList;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import es.ucm.fdi.iw.model.User;

/**
 * Implementación de {@link UserDetailsService} que autentica usuarios
 * contra la base de datos JPA de la aplicación.
 * <p>
 * Spring Security invoca {@link #loadUserByUsername} durante el proceso
 * de login para obtener las credenciales y roles del usuario.
 */
public class IwUserDetailsService implements UserDetailsService {

	private static Logger log = LogManager.getLogger(IwUserDetailsService.class);

    private EntityManager entityManager;

    @PersistenceContext
    public void setEntityManager(EntityManager em){
        this.entityManager = em;
    }

    /**
     * Carga un usuario por nombre de usuario y construye el objeto {@link UserDetails}
     * que Spring Security necesita para la autenticación.
     *
     * @param username nombre de usuario
     * @return objeto {@link UserDetails} con credenciales y roles
     * @throws UsernameNotFoundException si el usuario no existe o está deshabilitado
     */
    public UserDetails loadUserByUsername(String username){
    	try {
	        User u = entityManager.createNamedQuery("User.byUsername", User.class)
                    .setParameter("username", username)
                    .getSingleResult();
	        ArrayList<SimpleGrantedAuthority> roles = new ArrayList<>();
	        for (String r : u.getRoles().split("[,]")) {
	        	roles.add(new SimpleGrantedAuthority("ROLE_" + r));
		        log.info("Roles for " + username + " include " + roles.get(roles.size()-1));
	        }
	        return new org.springframework.security.core.userdetails.User(
	        		u.getUsername(), u.getPassword(), roles);
	    } catch (Exception e) {
    		log.info("No such user: " + username + " (error = " + e.getMessage() + ")");
    		throw new UsernameNotFoundException(username);
    	}
    }
}