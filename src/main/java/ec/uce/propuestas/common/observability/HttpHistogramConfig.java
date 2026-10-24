package ec.uce.propuestas.common.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Duration;

/** Habilita histograma de latencia (buckets) para {@code http.server.requests}. */
public class HttpHistogramConfig {

    @Produces
    @Singleton
    public MeterFilter httpServerRequestsHistogram() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().equals("http.server.requests")) {
                    return DistributionStatisticConfig.builder()
                            .percentilesHistogram(true)
                            .minimumExpectedValue((double) Duration.ofMillis(5).toNanos())
                            .maximumExpectedValue(
                                    (double) Duration.ofSeconds(10).toNanos())
                            .build()
                            .merge(config);
                }
                return config;
            }
        };
    }
}
