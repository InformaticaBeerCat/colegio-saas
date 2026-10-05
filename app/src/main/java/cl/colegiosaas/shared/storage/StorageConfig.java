package cl.colegiosaas.shared.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.net.URI;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    FileStorage fileStorage(StorageProperties properties) {
        if ("s3".equalsIgnoreCase(properties.type())) {
            StorageProperties.S3 s3 = properties.s3();
            S3ClientBuilder builder = S3Client.builder()
                    .httpClient(UrlConnectionHttpClient.create())
                    .region(Region.of(s3.region()))
                    .forcePathStyle(s3.pathStyle())
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey(), s3.secretKey())));
            if (s3.endpoint() != null && !s3.endpoint().isBlank()) {
                builder.endpointOverride(URI.create(s3.endpoint()));
            }
            S3Client client = builder.build();
            ensureBucket(client, s3.bucket());
            log.info("Archivos en el bucket S3 {}", s3.bucket());
            return new S3FileStorage(client, s3.bucket());
        }
        log.info("Archivos en la carpeta {}", Path.of(properties.localDir()).toAbsolutePath());
        return new LocalFileStorage(Path.of(properties.localDir()));
    }

    /** En MinIO recién instalado el bucket no existe: se crea al arrancar. */
    private static void ensureBucket(S3Client client, String bucket) {
        try {
            client.headBucket(b -> b.bucket(bucket));
        } catch (NoSuchBucketException e) {
            client.createBucket(b -> b.bucket(bucket));
        }
    }
}
