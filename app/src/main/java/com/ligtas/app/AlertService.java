package com.ligtas.app;
import android.util.Log;

public class AlertService {
    private static final String TAG = "LigtasOOP";

    public boolean sendAlert(User sender, Alert alert) {
        // Polymorphism in action: Java automatically knows if sender is Student or Admin
        if (sender.canSendAlerts()) {
            alert.setStatus("SENT");
            Log.d(TAG, "SUCCESS: " + alert.getSeverityLevel() + " alert sent by " + sender.getName());
            // Future step: Here is where we will trigger Firebase to push to phones
            return true;
        } else {
            Log.e(TAG, "DENIED: " + sender.getName() + " does not have permission to send alerts.");
            return false;
        }
    }
}