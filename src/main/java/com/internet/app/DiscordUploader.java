package com.internet.app;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DiscordUploader {

    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    public static void upload(String url, File file) throws IOException {
        RequestBody body = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.getName(),
                RequestBody.create(MediaType.parse("application/zip"), file))
            .build();

        Request req = new Request.Builder()
            .url(url)
            .post(body)
            .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Upload failed: HTTP " + resp.code());
            }
        }
    }
}
