package com.ligtas.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Sign in with the school Google account, then look the person up on the roster.
 * Only people on the roster get in, and their ROLE comes from the roster (never from the phone).
 */
public class LoginActivity extends AppCompatActivity {


    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private LoginViewModel loginVm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_compose);
        LigtasUi.applyChrome(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        loginVm = new ViewModelProvider(this).get(LoginViewModel.class);
        LoginBridge.attach((ComposeView) findViewById(R.id.composeLogin), loginVm, this::startGoogleSignIn);

        FirebaseUser existing = auth.getCurrentUser();
        if (existing != null) {
            // Already signed in earlier: re-check the roster (the role may have changed)
            setBusy("Checking your account...");
            checkRoster(existing);
        } else {
            // Signed out: make sure this phone is not still receiving campus alerts
            FirebaseMessaging.getInstance().unsubscribeFromTopic(Topics.CAMPUS);
        }
    }

    private void startGoogleSignIn() {
        setBusy("Opening Google sign-in...");
        GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .setAutoSelectEnabled(false)
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();

        CredentialManager.create(this).getCredentialAsync(
                this, request, new CancellationSignal(), ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        onCredential(result.getCredential());
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            fail("Sign-in cancelled.");
                        } else if (e instanceof NoCredentialException) {
                            fail("No Google account found. Add your account in Settings > Accounts, then try again.");
                        } else {
                            fail("Sign-in problem: " + e.getType() + " " + e.getMessage());
                        }
                    }
                });
    }

    private void onCredential(Credential credential) {
        if (credential instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            try {
                GoogleIdTokenCredential google =
                        GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
                AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(google.getIdToken(), null);
                auth.signInWithCredential(firebaseCredential).addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        setBusy("Checking your account...");
                        checkRoster(auth.getCurrentUser());
                    } else {
                        String why = task.getException() != null ? task.getException().getMessage() : "unknown error";
                        fail("Sign-in failed: " + why);
                    }
                });
            } catch (Exception e) {
                fail("Could not read the Google account: " + e.getMessage());
            }
        } else {
            fail("Unexpected sign-in type.");
        }
    }

    /** The roster decides who is allowed in and what role they have. */
    private void checkRoster(FirebaseUser user) {
        String email = user.getEmail() == null ? "" : user.getEmail().toLowerCase();
        // No email-domain check here: the roster decides who may use the app (it only contains emails an admin added,
        // and students/teachers are limited to the school's allowed domains when they are added).
        if (email.isEmpty()) {
            auth.signOut();
            fail("This Google account has no email address.");
            return;
        }

        db.collection("roster").document(email).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.exists()) {
                        Topics.unsubscribeAll(this);   // e.g. graduated and removed: stop campus alerts on this phone
                        auth.signOut();
                        fail("This account (" + email + ") is not registered. Sign in with the account your administrator added, or contact the administrator.");
                        return;
                    }
                    String role = snap.getString("role");
                    if (!"ADMIN".equals(role) && !"TEACHER".equals(role) && !"STUDENT".equals(role)) {
                        auth.signOut();
                        fail("Your roster entry has no valid role. Contact the administrator.");
                        return;
                    }
                    Object cv = snap.get("consentVersion");
                    int agreed = cv instanceof Number ? ((Number) cv).intValue() : 0;
                    if (agreed >= ConsentNotice.VERSION) {
                        openApp(email, role, snap);
                    } else {
                        askConsent(email, role, snap);
                    }
                })
                .addOnFailureListener(e -> fail("Could not check the roster: " + e.getMessage()));
    }

    /** First sign-in (or a new notice version): the person must agree before anything else. */
    private void askConsent(String email, String role, com.google.firebase.firestore.DocumentSnapshot snap) {
        setBusy("");
        askConsentSheet(email, role, snap);
    }

    /** The privacy notice: a bottom sheet that cannot be dismissed without choosing. */
    private void askConsentSheet(String email, String role, com.google.firebase.firestore.DocumentSnapshot snap) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setDraggable(false);
        ConsentBridge.attach(dialog, this, ConsentNotice.TITLE, ConsentNotice.TEXT, new ConsentListener() {
            @Override public void onAgree() {
                dialog.dismiss();
                setBusy("Saving...");
                java.util.Map<String, Object> update = new java.util.HashMap<>();
                update.put("consentVersion", ConsentNotice.VERSION);
                update.put("consentAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                db.collection("roster").document(email).update(update)
                        .addOnSuccessListener(v -> openApp(email, role, snap))
                        .addOnFailureListener(e -> {
                            auth.signOut();
                            fail("Could not save your agreement: " + e.getMessage());
                        });
            }
            @Override public void onDecline() {
                dialog.dismiss();
                Topics.unsubscribeAll(LoginActivity.this);
                auth.signOut();
                fail("You declined, so you were signed out and will not receive alerts through Ligtas. Sign in again to review the notice.");
            }
        });
        // keep the dark glass look: no white box behind the Compose sheet
        dialog.setOnShowListener(d -> {
            android.view.View container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (container != null) container.setBackgroundResource(android.R.color.transparent);
        });
        dialog.show();
    }

    private void openApp(String email, String role, com.google.firebase.firestore.DocumentSnapshot snap) {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.putExtra("email", email);
                    intent.putExtra("role", role);
                    intent.putExtra("name", orEmpty(snap.getString("name")));
                    intent.putExtra("studentNo", orEmpty(snap.getString("studentNo")));
                    intent.putExtra("department", orEmpty(snap.getString("department")));
                    intent.putExtra("building", orEmpty(snap.getString("building")));
                    String college = orEmpty(snap.getString("college"));
                    intent.putExtra("college", college.isEmpty() ? orEmpty(snap.getString("department")) : college);
                    intent.putExtra("program", orEmpty(snap.getString("program")));
                    Object teachesRaw = snap.get("teaches");
                    if (teachesRaw instanceof java.util.List) {
                        java.util.ArrayList<String> teaches = new java.util.ArrayList<>();
                        for (Object o : (java.util.List<?>) teachesRaw) teaches.add(String.valueOf(o));
                        intent.putStringArrayListExtra("teaches", teaches);
                    }
                    Object yr = snap.get("yearLevel");
                    intent.putExtra("yearLevel", yr instanceof Number ? ((Number) yr).intValue()
                            : parseYear(String.valueOf(yr)));
                    intent.putExtra("campus", orEmpty(snap.getString("campus")));
                    // If the person got here by tapping an alert, pass it on so it can be acknowledged
                    Intent incoming = getIntent();
                    if (incoming != null && incoming.getStringExtra("alertId") != null) {
                        intent.putExtra("alertId", incoming.getStringExtra("alertId"));
                        intent.putExtra("alertTitle", incoming.getStringExtra("alertTitle"));
                        intent.putExtra("alertBody", incoming.getStringExtra("alertBody"));
                        intent.putExtra("alertType", incoming.getStringExtra("alertType"));
                        intent.putExtra("alertSentAt", incoming.getLongExtra("alertSentAt", 0));
                        intent.putExtra("alertBy", incoming.getStringExtra("alertBy"));
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
    }

    private static int parseYear(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private void setBusy(String message) {
        loginVm.setBusy(true);
        loginVm.setError(false);
        loginVm.setStatus(message);
    }

    private void fail(String message) {
        loginVm.setBusy(false);
        loginVm.setError(true);
        loginVm.setStatus(message);
    }
}
