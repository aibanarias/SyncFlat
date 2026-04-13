package es.ucm.fdi.iw;

import java.io.File;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.Environment;

/**
 * Configuración general de la aplicación.
 * <p>
 * Declara beans de infraestructura accesibles mediante inyección de dependencias
 * en cualquier componente gestionado por Spring.
 */
@Configuration
public class AppConfig {

	@Autowired
	private Environment env;

	/**
	 * Bean de acceso al sistema de ficheros local de la aplicación.
	 * La ruta base se lee de la propiedad {@code es.ucm.fdi.base-path}.
	 */
	@Bean(name="localData")
	public LocalData getLocalData() {
		return new LocalData(new File(env.getProperty("es.ucm.fdi.base-path")));
	}

	/**
	 * Fuente de mensajes para internacionalización (i18n).
	 * Carga los literales del fichero {@code Messages.properties}.
	 */
	@Bean
	public ResourceBundleMessageSource messageSource() {
		ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
		messageSource.setBasename("Messages");
		return messageSource;
	}
}
