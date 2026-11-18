package io.fulfillx.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.HashMap;
import java.util.Map;

/** Serializes W3C trace context into message metadata so asynchronous work joins the originating trace. */
public final class TraceContextCarrier {
    private static final TextMapSetter<Map<String, String>> SETTER = Map::put;
    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override public Iterable<String> keys(Map<String, String> carrier) { return carrier.keySet(); }
        @Override public String get(Map<String, String> carrier, String key) { return carrier.get(key); }
    };

    private TraceContextCarrier() { }

    public static Map<String, String> capture(OpenTelemetry telemetry) {
        Map<String, String> values = new HashMap<>();
        telemetry.getPropagators().getTextMapPropagator().inject(Context.current(), values, SETTER);
        return values;
    }

    public static Context extract(OpenTelemetry telemetry, Map<String, String> values) {
        return telemetry.getPropagators().getTextMapPropagator().extract(Context.root(), values, GETTER);
    }
}
