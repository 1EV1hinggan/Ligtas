package com.ligtas.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Asks the Ligtas sender service to push a message. The app holds NO Firebase Admin key any more:
 * it only proves who is signed in (a Firebase ID token), and the server checks that this person is an
 * admin in the roster before it sends anything.
 */
public class FcmSender {
    /** Paste the address of your deployed sender here (see sender/README.md), ending in /api/send. */
    private static final String SEND_URL = "https://ligtas-sender.vercel.app/api/send";

    /** How long FCM keeps trying to deliver before dropping the message (phones that were offline). */
    public static final int ALERT_TTL_SECONDS = 30 * 60;      // a lockdown that is 30+ minutes old is stale
    public static final int RESOLUTION_TTL_SECONDS = 2 * 60 * 60; // an all-clear is still worth delivering late

    private static final OkHttpClient CLIENT = new OkHttpClient();

    /** Sends any data payload to "/topics/name" or "condition:...", with an expiry (time-to-live) in seconds. */
    public void send(Context context, String target, Map<String, String> payload, int ttlSeconds) {
        final Context app = context.getApplicationContext();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            fail(app, "You are signed out. Sign in again.");
            return;
        }
        user.getIdToken(false).addOnSuccessListener(result -> post(app, result.getToken(), target, payload, ttlSeconds))
                .addOnFailureListener(e -> fail(app, "Could not confirm your sign-in: " + e.getMessage()));
    }

    private void post(Context app, String idToken, String target, Map<String, String> payload, int ttlSeconds) {
        try {
            JSONObject data = new JSONObject();
            for (Map.Entry<String, String> e : payload.entrySet()) {
                data.put(e.getKey(), e.getValue() == null ? "" : e.getValue());
            }
            JSONObject body = new JSONObject();
            body.put("target", target);
            body.put("data", data);
            body.put("ttl", ttlSeconds);

            Request request = new Request.Builder()
                    .url(SEND_URL)
                    .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                    .addHeader("Authorization", "Bearer " + idToken)
                    .build();

            CLIENT.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    fail(app, "No connection to the alert server: " + e.getMessage());
                }

                @Override public void onResponse(Call call, Response response) throws IOException {
                    try (Response r = response) {
                        String text = r.body() == null ? "" : r.body().string();
                        if (r.isSuccessful()) {
                            Log.d("LigtasFCM", "Push accepted by server (" + r.code() + ")");
                        } else {
                            fail(app, "Alert NOT sent (" + r.code() + "): " + text);
                        }
                    }
                }
            });
        } catch (Exception e) {
            fail(app, "Could not build the alert: " + e.getMessage());
        }
    }

    /** A failed alert must never go unnoticed: log it and tell the admin on screen. */
    private static void fail(Context app, String message) {
        Log.e("LigtasFCM", message);
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(app, message, Toast.LENGTH_LONG).show());
    }
}
