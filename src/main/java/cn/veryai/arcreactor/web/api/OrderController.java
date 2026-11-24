package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.actor.order.model.OrderState;
import cn.veryai.arcreactor.service.LeaseService;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final LeaseService service;

    @GetMapping("/{id}")
    public CompletionStage<OrderState> get(@PathVariable("id") String id) { return service.order(id); }
}
