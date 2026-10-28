package io.trellis.app.config;

import io.trellis.core.taskmanager.TaskLifecycleManager;
import io.trellis.core.taskmanager.TaskLifecycleService;
import io.trellis.app.config.converter.HookReadConverter;
import io.trellis.app.config.converter.TaskDependencyReadConverter;
import io.trellis.app.config.converter.ValidationHookReadConverter;
import io.trellis.app.context.TrellisTaskContext;
import io.trellis.app.context.TrellisTaskContextBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

/**
 * Everything the Trellis library modules expect a host application to provide:
 * auditing, repositories, converters, transactions, SpEL, the lifecycle service and a hook thread pool.
 */
@Configuration
@EnableMongoAuditing   // REQUIRED: Auditable.isNew() relies on createdAt being filled by auditing
@EnableMongoRepositories(basePackages = "io.trellis.db.repository")
@EnableAspectJAutoProxy
@EnableConfigurationProperties(TrellisRuntimeProperties.class)
public class TrellisRuntimeConfig {

    @Bean
    @ConditionalOnMissingBean
    public ExpressionParser expressionParser() {
        return new SpelExpressionParser();
    }

    /** Hook / ValidationHook / TaskDependency hold polymorphic configs; read them through Jackson. */
    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(adapter -> adapter
                .registerConverter(new HookReadConverter())
                .registerConverter(new ValidationHookReadConverter())
                .registerConverter(new TaskDependencyReadConverter()));
    }

    /**
     * WorkFlowServiceImpl.publishWorkflow is @Transactional. Mongo transactions need a replica set,
     * which docker-compose.yml provides (single node "rs0"). Turn off with trellis.mongo.transactions-enabled=false.
     */
    @Bean
    @ConditionalOnProperty(name = "trellis.mongo.transactions-enabled", havingValue = "true", matchIfMissing = true)
    public MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    /** The service the host calls to run one captured answer through the command chain. */
    @Bean
    public TaskLifecycleService<TrellisTaskContext> taskLifecycleService(TaskLifecycleManager<TrellisTaskContext> manager,
                                                                         TrellisTaskContextBuilder builder) {
        return new TaskLifecycleService<>(manager, builder);
    }

    /** Thread pool used for hooks marked "async": true. */
    @Bean(name = "trellisHookExecutor")
    public ThreadPoolTaskExecutor trellisHookExecutor(TrellisRuntimeProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getHooks().getAsyncPoolSize());
        executor.setMaxPoolSize(props.getHooks().getAsyncPoolSize() * 2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("trellis-hook-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "trellisRestClient")
    public RestClient trellisRestClient(RestClient.Builder builder) {
        return builder.build();
    }
}
