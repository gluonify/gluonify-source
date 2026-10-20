package io.gluonify.source;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;

/** A {@link SourceConfig} for plain unit tests: the values given, the defaults of {@code application.properties} otherwise. */
public final class TestConfig {
    private TestConfig() {}

    /** @param values keys such as {@code store}, {@code files.dir}, {@code graph.url}, {@code graph.user}, {@code graph.password}, {@code webhook.key}, {@code auth.client-id}, {@code auth.charm-url} */
    public static SourceConfig of(final Map<String, String> values) {
        return proxy(SourceConfig.class, "", values);
    }

    private static <T> T proxy(final Class<T> type, final String prefix, final Map<String, String> values) {
        final Object p = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
            final String key = prefix + kebab(method.getName());
            final Class<?> returned = method.getReturnType();
            if (returned.isInterface() && returned != Optional.class) return proxy(returned, key + ".", values);
            final String v = values.get(key);
            if (returned == Optional.class) return Optional.ofNullable(v);
            if (v != null) return v;
            return switch (key) {
                case "store" -> "auto";
                case "files.dir" -> "/distributed/std/notes";
                case "graph.database" -> "source";
                case "auth.scope" -> "openid profile";
                default -> throw new IllegalStateException("no default for " + key);
            };
        });
        return type.cast(p);
    }

    private static String kebab(final String name) {
        return name.replaceAll("([A-Z])", "-$1").toLowerCase(java.util.Locale.ROOT);
    }
}
