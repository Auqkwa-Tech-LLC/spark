package me.lucko.spark.common.grafana;

import java.util.List;

public class GrafanaAnnotation {
    private final Integer dashboardId;
    private final Integer panelId;
    private final Long time;
    private final Long timeEnd;
    private final List<String> tags;
    private final String text;

    public GrafanaAnnotation(Integer dashboardId, Integer panelId, Long time, Long timeEnd, List<String> tags, String text) {
        this.dashboardId = dashboardId;
        this.panelId = panelId;
        this.time = time;
        this.timeEnd = timeEnd;
        this.tags = tags;
        this.text = text;
    }

    public int getDashboardId() {
        return dashboardId;
    }

    public int getPanelId() {
        return panelId;
    }

    public long getTime() {
        return time;
    }

    public long getTimeEnd() {
        return timeEnd;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getText() {
        return text;
    }
}
