package com.example.demo.catalog;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="catalog.expiration.enabled",havingValue="true",matchIfMissing=true)
public class ProductExpirationJob {
    private final CatalogService service;
    public ProductExpirationJob(CatalogService service) { this.service=service; }
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() { service.expireProducts(); }
    @Scheduled(cron="0 * * * * *",zone="America/Sao_Paulo")
    public void expire() { service.expireProducts(); }
}
