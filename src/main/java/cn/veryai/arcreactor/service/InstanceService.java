package cn.veryai.arcreactor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class InstanceService {

    @Value("${pekko.instance.ask-timeout}")
    private Duration askTimeout;



}
