package me.lucko.spark.common.grafana;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.lucko.spark.common.util.AbstractHttpClient;
import okhttp3.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

public class GrafanaClient extends AbstractHttpClient {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson GSON = new GsonBuilder().create();

    private final String url;
    private final String authEmail;
    private final String serverId;

    public GrafanaClient(OkHttpClient okHttpClient, String url, String authEmail, String serverId) {
        super(okHttpClient);
        this.serverId = serverId;
        if (url.endsWith("/")) {
            this.url = url;
        } else {
            this.url = url + "/";
        }
        this.authEmail = authEmail;
    }

    public static GrafanaClient fromEnvironment(OkHttpClient okHttpClient) {
        String url = System.getProperty("me.lucko.spark.grafana.url");
        String serverId = System.getProperty("me.lucko.spark.grafana.server_id");
        if (url == null || serverId == null) return null;

        String authEmail = System.getProperty("me.lucko.spark.grafana.auth.email", "spark");

        return new GrafanaClient(okHttpClient, url, authEmail, serverId);
    }

    public List<String> getAnnotationTags() {
        return Arrays.asList(
                "spark",
                "server_id:" + serverId
        );
    }

    public int createAnnotation(String text, long startTime) throws IOException {
        GrafanaAnnotation annotation = new GrafanaAnnotation(null, null, startTime, null, getAnnotationTags(), text);

        RequestBody requestBody = RequestBody.create(JSON, GSON.toJson(annotation));
        Request request = new Request.Builder()
                .url(this.url + "api/annotations")
                .header("X-Auth-Request-Email", this.authEmail)
                .header("Accept", "application/json")
                .post(requestBody)
                .build();

        Response response = makeHttpRequest(request);
        return GSON.fromJson(new InputStreamReader(response.body().byteStream()), GrafanaAnnotationResponse.class).getId();
    }

    public void updateAnnotation(int annotationId, String url, long endTime) throws IOException {
        String text = "<a href=\"" + url + "\" target=\"_blank\">" + url + "</a>";
        GrafanaAnnotation annotation = new GrafanaAnnotation(null, null, null, endTime, getAnnotationTags(), text);

        RequestBody requestBody = RequestBody.create(JSON, GSON.toJson(annotation));
        Request request = new Request.Builder()
                .url(this.url + "api/annotations/" + annotationId)
                .header("X-Auth-Request-Email", this.authEmail)
                .header("Accept", "application/json")
                .patch(requestBody)
                .build();

        makeHttpRequest(request);
    }
}
