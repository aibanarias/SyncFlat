package es.ucm.fdi.iw;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad de la aplicación.
 * <p>
 * Define las reglas de acceso por URL, el formulario de login, la gestión
 * de contraseñas y el proveedor de autenticación basado en JPA.
 * Las reglas se evalúan en orden: la primera que coincide es la que se aplica.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Autowired
	private Environment env;

	/**
	 * Cadena de filtros de seguridad HTTP.
	 * <p>
	 * Configura qué rutas son públicas, cuáles requieren autenticación y cuáles
	 * están restringidas a roles concretos. En modo debug se habilita además
	 * el acceso a la consola H2.
	 */
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

		// acceso a consola h2 en modo debug
		String debugProperty = env.getProperty("es.ucm.fdi.debug");
		if (debugProperty != null && Boolean.parseBoolean(debugProperty.toLowerCase())) {
			http.csrf(csrf -> csrf
					.ignoringRequestMatchers("/h2/**"));
			http.authorizeHttpRequests(authorize -> authorize
					.requestMatchers("/h2/**").permitAll()
			);
			http.headers(header -> header.frameOptions(frameOptions -> frameOptions.sameOrigin()));
		}

		http
				.csrf(csrf -> csrf
						.ignoringRequestMatchers("/api/**"))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/css/**", "/js/**", "/img/**", "/webjars/**", "/", "/error").permitAll()
						.requestMatchers("/autores").permitAll()
						.requestMatchers("/api/**").permitAll()
						.requestMatchers("/modulos/**").authenticated()
						.requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
						.requestMatchers("/user/**").hasRole("USER")
						.anyRequest().authenticated())
				.formLogin(formLogin -> formLogin
						.loginPage("/login")
						.permitAll()
						.successHandler(loginSuccessHandler)
				);

		return http.build();
	}

	/**
	 * Codificador de contraseñas. Utiliza la estrategia delegada de Spring Security,
	 * que aplica bcrypt por defecto e incluye el identificador del algoritmo en el hash.
	 */
	@Bean
	public PasswordEncoder getPasswordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	/**
	 * Servicio que carga los datos de usuario desde la base de datos JPA para
	 * que Spring Security pueda realizar la autenticación.
	 */
	@Bean
	public IwUserDetailsService springDataUserDetailsService() {
		return new IwUserDetailsService();
	}

	/**
	 * Proveedor de autenticación que combina el {@link UserDetailsService} con
	 * el codificador de contraseñas. Exponerlo como bean permite usarlo para
	 * autenticar usuarios programáticamente (p. ej., tras el registro).
	 */
	@Bean
	public AuthenticationManager authenticationManager(
			UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
		authenticationProvider.setUserDetailsService(userDetailsService);
		authenticationProvider.setPasswordEncoder(passwordEncoder);

		return new ProviderManager(authenticationProvider);
	}

	@Autowired
	private LoginSuccessHandler loginSuccessHandler;
}
