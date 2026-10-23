package cn.veryai.arcreactor.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Looks up messages by code using an isolated Spring MessageSource.
 * Missing translations fall back to the Chinese base bundle; unknown codes return the code.
 */
@Service
public class MessageService {
    private final MessageSource messageSource;

    public MessageService() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        this.messageSource = source;
    }

    /** Uses the current Spring locale, including the request locale in Spring MVC. */
    public String getMessage(String code, Object... args) {
        return getMessage(code, LocaleContextHolder.getLocale(), args);
    }

    /** A null locale uses the current Spring locale. Arguments replace {0}, {1}, etc. */
    public String getMessage(String code, Locale locale, Object... args) {
        Assert.hasText(code, "Message code must not be blank");
        Locale resolvedLocale = locale != null ? locale : LocaleContextHolder.getLocale();
        return messageSource.getMessage(code, args, code, resolvedLocale);
    }

    public String getMessage(int code, Object... args) {
        return getMessage(Integer.toString(code), args);
    }

    public String getMessage(int code, Locale locale, Object... args) {
        return getMessage(Integer.toString(code), locale, args);
    }
}
