package com.ligtas.app;

import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * A teacher's "Account for my room" report: the sheet, saving it locally, sending it to the administrators.
 * Moved out of MainActivity; the behaviour is unchanged. onChanged refreshes the home-screen button text.
 */
final class RoomReportController {
    private final AppCompatActivity activity;
    private final Supplier<String> userEmail;
    private final Supplier<String> userName;
    private final Runnable onChanged;

    RoomReportController(AppCompatActivity activity, Supplier<String> userEmail,
                         Supplier<String> userName, Runnable onChanged) {
        this.activity = activity;
        this.userEmail = userEmail;
        this.userName = userName;
        this.onChanged = onChanged;
    }

    /** Asks which room, whether everyone is accounted for, and sends it to the administrators. */
    void show(final String alertId) {
        final RoomReportStore.Mine mine = RoomReportStore.get(activity, alertId);
        final String[] codes = {RoomReport.SAFE, RoomReport.MISSING, RoomReport.HELP};

        final RoomReportState st = new RoomReportState();
        st.setRoom(mine != null && !mine.room.isEmpty() ? mine.room : RoomReportStore.lastRoom(activity));
        for (String code : codes) st.getStatusLabels().add(RoomReport.label(code));
        for (int i = 0; i < codes.length; i++) {
            if (mine != null && codes[i].equals(mine.status)) st.setStatusIndex(i);
        }

        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        RoomReportBridge.attach(dialog, activity, st, new RoomReportListener() {
            @Override public void onCancel() { dialog.dismiss(); }
            @Override public void onSend() {
                String room = st.getRoom().trim();
                if (room.isEmpty()) { st.setRoomError("Which room are you in?"); return; }
                if (st.getStatusIndex() < 0) { st.setStatusError("Choose one of the three options."); return; }
                int count = -1;
                String c = st.getCount().trim();
                if (!c.isEmpty()) { try { count = Integer.parseInt(c); } catch (NumberFormatException e) { count = -1; } }
                sendRoomReport(alertId, new RoomReport(userEmail.get(), userName.get(), room, codes[st.getStatusIndex()], count,
                        st.getNote(), 0));
                dialog.dismiss();
            }
        });
        SheetUi.makeTransparent(dialog);
        dialog.show();
    }

    private void sendRoomReport(final String alertId, final RoomReport report) {
        RoomReportStore.save(activity, alertId, report.room, report.status, RoomReportStore.SENDING);
        onChanged.run();
        AlertTracker.saveRoomReport(alertId, report, new AlertTracker.Callback<Void>() {
            @Override public void onSuccess(Void value) {
                RoomReportStore.setDelivery(activity, alertId, RoomReportStore.SENT);
                // Reporting your room shows you know about the alert, so count it as an acknowledgment too
                AlertTracker.markAcknowledged(alertId, null);
                LastAlertStore.markAcked(activity, alertId);
                onChanged.run();
                Toast.makeText(activity, "Sent to the administrators: " + report.room + ", "
                        + RoomReport.label(report.status).toLowerCase(Locale.US) + ".", Toast.LENGTH_LONG).show();
            }
            @Override public void onError(String message) {
                RoomReportStore.setDelivery(activity, alertId, RoomReportStore.FAILED);
                onChanged.run();
                Toast.makeText(activity, "Report not sent: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
