package cn.veryai.arcreactor.service;

import lombok.NonNull;
import org.apache.pekko.actor.typed.ActorSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class InstanceService {

    @Autowired
    private ActorSystem<?> system;

    @Value("${pekko.instance.ask-timeout}")
    private Duration askTimeout;



}
