package demo.reactividad.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(
    String bucketName,
    String endpoint,
    String region,
    String accessKey,
    String secretKey
) {

}
