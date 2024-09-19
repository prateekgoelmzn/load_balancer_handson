package com.pg.uuid_generator.controller;

import com.pg.uuid_generator.model.UUIDResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/uuid")
public class AppController {

    Logger log = LoggerFactory.getLogger(this.getClass());

    @Value("${INSTANCE_ID:default}")
    private String instanceId;

    @GetMapping("/get")
    public ResponseEntity<UUIDResponse> getUUID() {
        UUID uuid = UUID.randomUUID();
        var res = new UUIDResponse(uuid.toString(), instanceId);
        log.info("{}",res);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/get-slow")
    public ResponseEntity<UUIDResponse> getUUIDSlow() throws InterruptedException {
        UUID uuid = UUID.randomUUID();
        var res = new UUIDResponse(uuid.toString(), instanceId);
        log.info("{}",res);
        Thread.sleep(Duration.ofSeconds(20).toMillis());
        return ResponseEntity.ok(res);
    }

    @GetMapping("/get/error")
    public ResponseEntity<UUIDResponse> getUUIDError(){
        return ResponseEntity.internalServerError().build();
    }
}
