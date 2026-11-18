package br.com.feedflow.avaliacao.persistence;

import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

@Singleton
public class DynamoDbClientProducer {

    private static final String ACCESS_KEY_LOCAL = "DUMMYIDEXAMPLE";
    private static final String SECRET_KEY_LOCAL = "DUMMYEXAMPLEKEY";

    @Produces
    @Singleton
    DynamoDbClient produzir(DynamoDbConfig config) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(config.region()))
                .httpClientBuilder(UrlConnectionHttpClient.builder());

        config.endpointOverride().ifPresentOrElse(
                endpoint -> builder
                        .endpointOverride(endpoint)
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(ACCESS_KEY_LOCAL, SECRET_KEY_LOCAL))),
                () -> builder.credentialsProvider(DefaultCredentialsProvider.builder().build()));

        return builder.build();
    }

    void fechar(@Disposes DynamoDbClient client) {
        client.close();
    }
}
