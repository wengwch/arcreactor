package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.service.ShareService;
import cn.veryai.arcreactor.vo.ShareView;
import cn.veryai.arcreactor.web.params.CreateShareParam;
import jakarta.validation.Valid;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/{userId}/shares")
@RequiredArgsConstructor
public class ShareController {
    private final ShareService shareService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletionStage<ShareView> create(@PathVariable("userId") String userId,
            @Valid @RequestBody CreateShareParam param) {
        return shareService.create(userId, param);
    }

    @GetMapping("/{shareId}")
    public CompletionStage<ShareView> get(@PathVariable("userId") String userId,
            @PathVariable("shareId") String shareId) {
        return shareService.get(userId, shareId);
    }
}
