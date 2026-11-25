package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.actor.order.model.OrderState;
import cn.veryai.arcreactor.service.LeaseService;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final LeaseService leaseService;

    @GetMapping("/{orderId}")
    public CompletionStage<OrderState> getOrder(@PathVariable("orderId") String orderId) {
        return leaseService.getOrder(orderId);
    }
}
