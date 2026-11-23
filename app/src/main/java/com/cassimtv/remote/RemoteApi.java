package com.cassimtv.remote;

import org.json.JSONObject;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class RemoteApi {
    public static final String BASE_URL = "https://cassimtv3.onrender.com";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private RemoteApi() {}

    public static JSONObject post(String path, JSONObject body, String bearer) throws Exception {
        Request.Builder builder = new Request.Builder()
                .url(BASE_URL + path)
                .post(RequestBody.create(JSON, body.toString()))
                .header("Accept", "application/json");
        if (bearer != null && !bearer.isEmpty()) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        try (Response response = CLIENT.newCall(builder.build()).execute()) {
            String text = response.body() == null ? "{}" : response.body().string();
            JSONObject result = new JSONObject(text);
            if (!response.isSuccessful()) {
                throw new IOException(result.optString("detail", "Server request failed (" + response.code() + ")."));
            }
            return result;
        }
    }

    public static OkHttpClient client() {
        return CLIENT;
    }
}
