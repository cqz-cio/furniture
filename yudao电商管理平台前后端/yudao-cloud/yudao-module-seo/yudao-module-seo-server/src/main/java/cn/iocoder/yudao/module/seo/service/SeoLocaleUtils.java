package cn.iocoder.yudao.module.seo.service;

import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.LOCALE_INVALID;

/**
 * Shared BCP 47 locale validation and canonicalization for SEO contracts.
 */
public final class SeoLocaleUtils {

    private static final Pattern LANGUAGE_TAG_PATTERN =
            Pattern.compile("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$");

    private SeoLocaleUtils() {
    }

    public static String normalize(String locale) {
        String candidate = locale == null ? "" : locale.trim();
        if (candidate.contains("_") || !LANGUAGE_TAG_PATTERN.matcher(candidate).matches()) {
            throw exception(LOCALE_INVALID);
        }
        try {
            String normalized = new Locale.Builder().setLanguageTag(candidate).build().toLanguageTag();
            if ("und".equals(normalized)) {
                throw exception(LOCALE_INVALID);
            }
            return normalized;
        } catch (IllformedLocaleException ex) {
            throw exception(LOCALE_INVALID);
        }
    }

}
