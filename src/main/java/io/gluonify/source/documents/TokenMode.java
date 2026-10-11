package io.gluonify.source.documents;

import java.util.Locale;
import java.util.Optional;

/**
 * Where the token presented to Crystallon comes from ({@code source.crystallon.token}). The setting names a MODE; it never holds a token.
 * <ul>
 *   <li>{@link #FORWARDING} (default): the bearer token of the person who called this application. Crystallon sees that person (their roles, their organization).</li>
 *   <li>{@link #PLATFORM}: the application's OWN token, which the platform provides to an application deployed with {@code "uses": ["crystallon"]}
 *       (file {@code GLUONIFY_CRYSTALLON_TOKEN_FILE}, renewed by the node agent). It works outside a request too: a timer, a webhook, a message queue.</li>
 * </ul>
 */
public enum TokenMode {
    FORWARDING,
    PLATFORM;

    /** The value of the setting. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The mode of {@code source.crystallon.token} as SmallRye reads it. */
    public static TokenMode of(Optional<String> setting) {
        return parse(setting.orElse(null));
    }

    /**
     * @param value the setting: {@code forwarding} or {@code platform}, in any case; {@code null} or blank = {@code forwarding}
     * @throws IllegalStateException for any other value. The message never repeats the value: someone who mistook this setting for the token itself would otherwise write the token in the logs.
     */
    public static TokenMode parse(String value) {
        if (value == null || value.isBlank()) return FORWARDING;
        String v = value.strip().toLowerCase(Locale.ROOT);
        for (TokenMode m : values()) if (m.id().equals(v)) return m;
        throw new IllegalStateException("source.crystallon.token must be \"forwarding\" (the token of the caller, default) or \"platform\" (the application's own token): it chooses where the token comes from, it is not the token");
    }
}
