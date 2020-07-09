package me.lucko.spark.common.grafana;

public class GrafanaAnnotationResponse {
    private final int id;
    private final String message;

    public GrafanaAnnotationResponse(int id, String message) {
        this.id = id;
        this.message = message;
    }

    public int getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }
}
