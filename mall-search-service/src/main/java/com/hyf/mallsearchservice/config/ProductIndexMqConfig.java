package com.hyf.mallsearchservice.config;

import com.hyf.mallcommon.core.constant.MallConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Product search index MQ consumer topology.
 */
@Configuration
public class ProductIndexMqConfig {

    @Bean
    public DirectExchange productIndexExchange() {
        return new DirectExchange(MallConstants.MQ_PRODUCT_INDEX_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange productIndexDlx() {
        return new DirectExchange(MallConstants.MQ_PRODUCT_INDEX_DLX, true, false);
    }

    @Bean
    public Queue productIndexQueue() {
        return QueueBuilder.durable(MallConstants.MQ_PRODUCT_INDEX_QUEUE)
                .deadLetterExchange(MallConstants.MQ_PRODUCT_INDEX_DLX)
                .deadLetterRoutingKey(MallConstants.MQ_PRODUCT_INDEX_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue productIndexDlq() {
        return QueueBuilder.durable(MallConstants.MQ_PRODUCT_INDEX_DLQ).build();
    }

    @Bean
    public Binding productIndexUpsertBinding(@Qualifier("productIndexExchange") DirectExchange productIndexExchange,
                                             Queue productIndexQueue) {
        return BindingBuilder.bind(productIndexQueue)
                .to(productIndexExchange)
                .with(MallConstants.MQ_PRODUCT_INDEX_UPSERT_ROUTING_KEY);
    }

    @Bean
    public Binding productIndexDeleteBinding(@Qualifier("productIndexExchange") DirectExchange productIndexExchange,
                                             Queue productIndexQueue) {
        return BindingBuilder.bind(productIndexQueue)
                .to(productIndexExchange)
                .with(MallConstants.MQ_PRODUCT_INDEX_DELETE_ROUTING_KEY);
    }

    @Bean
    public Binding productIndexDlqBinding(@Qualifier("productIndexDlx") DirectExchange productIndexDlx,
                                          Queue productIndexDlq) {
        return BindingBuilder.bind(productIndexDlq)
                .to(productIndexDlx)
                .with(MallConstants.MQ_PRODUCT_INDEX_DLQ_ROUTING_KEY);
    }
}
