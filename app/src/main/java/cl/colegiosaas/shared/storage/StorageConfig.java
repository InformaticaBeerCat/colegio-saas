package cl.colegiosaas.shared.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
class StorageConfig {

    @Bean
    FileStorage fileStorage(@Value("${app.storage.local-dir}") String directory) {
        return new LocalFileStorage(Path.of(directory));
    }
}
