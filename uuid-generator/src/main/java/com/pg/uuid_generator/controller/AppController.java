package com.pg.uuid_generator.controller;

import com.pg.uuid_generator.model.UUIDResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/get-id")
    public ResponseEntity<UUIDResponse> getUUIDRequestParam(@RequestParam(name="id", required = false) String id) {
        UUID uuid = UUID.randomUUID();
        var res = new UUIDResponse(String.format("id %s : uuid %s",id, uuid), instanceId);
        log.info("{}",res);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/path/get/{id}")
    public ResponseEntity<UUIDResponse> getUUIDPathVariable(@PathVariable String id) {
        UUID uuid = UUID.randomUUID();
        var res = new UUIDResponse(String.format("id %s : uuid %s",id, uuid), instanceId);
        log.info("{}",res);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/get-slow")
    public ResponseEntity<UUIDResponse> getUUIDSlow() throws InterruptedException {
        Thread.sleep(Duration.ofSeconds(30).toMillis());
        UUID uuid = UUID.randomUUID();
        var res = new UUIDResponse(uuid.toString(), instanceId);
        log.info("{}",res);
        return ResponseEntity.ok(res);
    }
}
