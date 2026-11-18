package br.com.feedflow.avaliacao.persistence;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.net.URI;
import java.util.Optional;

@ConfigMapping(prefix = "feedflow.dynamodb")
public interface DynamoDbConfig {

    @WithDefault("FeedFlowFeedback")
    String tableName();

    @WithDefault("us-east-2")
    String region();

    @WithDefault("100")
    int queryPageSize();

    Optional<URI> endpointOverride();
}
