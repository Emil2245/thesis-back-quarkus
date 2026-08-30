package ec.uce.propuestas.common.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Plan 014 — Display config global (T3).
 *
 * <p>Bindea las propiedades bajo {@code app.display.*} a un record inmutable:
 * <ul>
 *   <li>{@code app.display.precision} → {@link #precision()} (decimales de dinero).</li>
 *   <li>{@code app.display.precision-porcentaje} → {@link #precisionPorcentaje()} (decimales de porcentaje).</li>
 * </ul>
 *
 * <p>Defaults: {@code 2} (dinero) y {@code 4} (porcentaje); ambos pueden ser
 * sobreescritos por variables de entorno o {@code application.yml}.
 *
 * <p>Vive en {@code common/config/}: <b>nada</b> de configuración, CDI ni REST
 * entra en {@code motor/} (regla de motor puro del Plan 014).
 */
@ConfigMapping(prefix = "app.display")
public interface DisplayConfig {

    @WithName("precision")
    @WithDefault("2")
    int precision();

    @WithName("precision-porcentaje")
    @WithDefault("4")
    int precisionPorcentaje();
}
