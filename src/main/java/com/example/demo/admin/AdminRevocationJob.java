package com.example.demo.admin;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class AdminRevocationJob {
    private final AdminAccessService access;
    public AdminRevocationJob(AdminAccessService access) {this.access=access;}
    @EventListener(ApplicationReadyEvent.class)
    public void startup() { access.revokeRemoved(); }
    @Scheduled(fixedDelay=60000)
    public void reconcile() { access.revokeRemoved(); }
}
