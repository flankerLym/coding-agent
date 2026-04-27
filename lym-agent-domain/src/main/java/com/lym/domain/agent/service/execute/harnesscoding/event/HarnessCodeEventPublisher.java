package com.lym.domain.agent.service.execute.harnesscoding.event;

import com.alibaba.fastjson.JSON;
import com.lym.domain.agent.service.execute.harnesscoding.model.HarnessCodeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

@Slf4j
@Component
public class HarnessCodeEventPublisher {

    public void publish(ResponseBodyEmitter emitter, HarnessCodeEvent event) {
        try {
            emitter.send(JSON.toJSONString(event) + "\n");
        } catch (Exception e) {
            log.warn("HarnessCode SSE event send failed, event={}", JSON.toJSONString(event), e);
        }
    }
}
