package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.service.UserCleanupService;
import cn.veryai.arcreactor.vo.UserCleanupView;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/{userId}/cleanup")
@RequiredArgsConstructor
public class UserCleanupController {
    private final UserCleanupService userCleanupService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletionStage<UserCleanupView> cleanup(@PathVariable("userId") String userId) {
        return userCleanupService.cleanup(userId);
    }

    @GetMapping
    public CompletionStage<UserCleanupView> get(@PathVariable("userId") String userId) {
        return userCleanupService.getCleanup(userId);
    }
}
