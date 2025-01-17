package com.elorankingbot.configuration;

import com.elorankingbot.logging.CustomFallbackEntityRetrieverWithAddedCacheMissLogging;
import com.elorankingbot.service.Services;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.retriever.RestEntityRetriever;
import discord4j.core.retriever.StoreEntityRetriever;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@CommonsLog
@Configuration
public class GatewayDiscordClientConfiguration {

    @Bean
    public GatewayDiscordClient createClient(Services services) {
        String botToken = services.props.isUseDevBotToken() ?
                System.getenv("DEV_BOT_TOKEN")
                : System.getenv(MTE1OTkzOTUwMjk3MDc3MzU2Nw.GpMehu.AerhNgRdjNFPTRVWO50Fes8li3H45CBw0XNH-4);
        GatewayDiscordClient client = DiscordClient.create(botToken)
                .gateway()
                .setEntityRetrievalStrategy(gateway -> new CustomFallbackEntityRetrieverWithAddedCacheMissLogging(
                        new StoreEntityRetriever(gateway), new RestEntityRetriever(gateway)))
                .setEnabledIntents(IntentSet.nonPrivileged().andNot(IntentSet.of(Intent.MESSAGE_CONTENT)))
                .login().block();
        return client;
    }
}
