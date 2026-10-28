package com.ligtas.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Asks the Ligtas server to re-alert the people who have not acknowledged an alert.
 * The phone only names the alert: the server decides who is left, finds their devices and enforces the limits.
 */
final class EscalationClient {
    private static final String URL = "https://ligtas-sender.vercel.app/api/escalate";
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static final class Result {
        int round, targeted, sent, failed, noPhone;
    }

    private EscalationClient() { }

    /** auto = true when the countdown ran out (the server double-checks that it really is due). */
    static void escalate(Context context, String alertId, boolean auto, AlertTracker.Callback<Result> cb) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { done(cb, null, "You are signed out. Sign in again."); return; }
        user.getIdToken(false).addOnSuccessListener(t -> post(t.getToken(), alertId, auto, cb))
                .addOnFailureListener(e -> done(cb, null, "Could not confirm your sign-in: " + e.getMessage()));
    }

    private static void post(String idToken, String alertId, boolean auto, AlertTracker.Callback<Result> cb) {
        try {
            JSONObject body = new JSONObject().put("alertId", alertId).put("mode", auto ? "auto" : "manual");
            Request request = new Request.Builder().url(URL)
                    .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                    .addHeader("Authorization", "Bearer " + idToken).build();
            CLIENT.newCall(request).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    done(cb, null, "No connection to the alert server.");
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    try (Response r = response) {
                        String text = r.body() == null ? "" : r.body().string();
                        JSONObject j = new JSONObject(text.isEmpty() ? "{}" : text);
                        if (r.isSuccessful()) {
                            Result res = new Result();
                            res.round = j.optInt("round"); res.targeted = j.optInt("targeted"); res.sent = j.optInt("sent");
                            res.failed = j.optInt("failed"); res.noPhone = j.optInt("noPhone");
                            done(cb, res, null);
                        } else {
                            done(cb, null, j.optString("error", "Server said " + r.code()));
                        }
                    } catch (Exception e) {
                        done(cb, null, "Unexpected reply from the alert server.");
                    }
                }
            });
        } catch (Exception e) {
            done(cb, null, String.valueOf(e.getMessage()));
        }
    }

    private static void done(AlertTracker.Callback<Result> cb, Result r, String error) {
        MAIN.post(() -> { if (error == null) cb.onSuccess(r); else cb.onError(error); });
    }
}
