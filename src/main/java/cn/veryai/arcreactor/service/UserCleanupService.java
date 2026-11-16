package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.actor.user.UserResources;
import cn.veryai.arcreactor.actor.user.reply.UserCleanupReply;
import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.vo.UserCleanupView;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserCleanupService {
    private final UserResources userResources;

    public CompletionStage<UserCleanupView> cleanup(String userId) {
        return request(userId, () -> userResources.cleanup(userId));
    }

    public CompletionStage<UserCleanupView> getCleanup(String userId) {
        return request(userId, () -> userResources.getCleanup(userId));
    }

    private CompletionStage<UserCleanupView> request(String userId,
            Supplier<CompletionStage<UserCleanupReply>> ask) {
        if (userId == null || userId.isBlank() || userId.length() > 255) {
            throw new BaseException(ErrorCode.BAD_REQUEST);
        }
        try {
            return ask.get().handle((reply, error) -> {
                if (error != null) throw operationFailure(error);
                if (reply == null || reply.status() == null || !userId.equals(reply.userId())) {
                    throw new BaseException(ErrorCode.BAD_GATEWAY);
                }
                if (reply.status() == UserCleanupReply.Status.REJECTED) {
                    throw new BaseException(ErrorCode.CONFLICT);
                }
                return UserCleanupView.from(reply);
            });
        } catch (RuntimeException exception) {
            throw operationFailure(exception);
        }
    }

    private static BaseException operationFailure(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof BaseException business) return business;
        return new BaseException(cause instanceof TimeoutException
                ? ErrorCode.GATEWAY_TIMEOUT : ErrorCode.BAD_GATEWAY, cause);
    }
}
