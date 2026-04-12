package com.hadi.jarvis;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // ── UI
    private TextView tvStatus;
    private TextView tvCommand;
    private TextView tvResponse;
    private ImageButton btnMic;

    // ── Speech
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private boolean ttsReady = false;
    private boolean isListening = false;

    // ── Handler for delayed actions
    private final Handler handler = new Handler(Looper.getMainLooper());

    // ─────────────────────────────────────────────
    //  LIFECYCLE
    // ─────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind views
        tvStatus   = findViewById(R.id.tvStatus);
        tvCommand  = findViewById(R.id.tvCommand);
        tvResponse = findViewById(R.id.tvResponse);
        btnMic     = findViewById(R.id.btnMic);

        tvResponse.setMovementMethod(new ScrollingMovementMethod());

        // Request mic permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.RECORD_AUDIO}, 1);
        }

        // Init TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                tts.setSpeechRate(0.95f);
                tts.setPitch(1.0f);
                ttsReady = true;
                runOnUiThread(() -> {
                    setStatus("Ready", "#4CAF50");
                    speak("JARVIS online. How can I help you?");
                    appendResponse("JARVIS: JARVIS online. How can I help?");
                });
            }
        });

        // Init Speech Recognizer
        initSpeechRecognizer();

        // Mic button
        btnMic.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            } else {
                startListening();
            }
        });
    }

    // ─────────────────────────────────────────────
    //  SPEECH RECOGNIZER SETUP
    // ─────────────────────────────────────────────
    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_LONG).show();
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                runOnUiThread(() -> {
                    isListening = true;
                    setStatus("Listening...", "#2196F3");
                    btnMic.setImageResource(R.drawable.ic_mic_active);
                });
            }

            @Override
            public void onBeginningOfSpeech() {
                runOnUiThread(() -> setStatus("Hearing you...", "#FF9800"));
            }

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                runOnUiThread(() -> setStatus("Processing...", "#9C27B0"));
            }

            @Override
            public void onError(int error) {
                runOnUiThread(() -> {
                    isListening = false;
                    setStatus("Ready — tap mic", "#4CAF50");
                    btnMic.setImageResource(R.drawable.ic_mic);
                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        appendResponse("JARVIS: Didn't catch that. Tap mic and try again.");
                    }
                });
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String command = matches.get(0).toLowerCase().trim();
                    runOnUiThread(() -> {
                        isListening = false;
                        btnMic.setImageResource(R.drawable.ic_mic);
                        tvCommand.setText("You: " + command);
                        setStatus("Ready — tap mic", "#4CAF50");
                        handleCommand(command);
                    });
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partial =
                        partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial != null && !partial.isEmpty()) {
                    runOnUiThread(() -> tvCommand.setText("You: " + partial.get(0) + "..."));
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startListening() {
        if (isListening) return;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        speechRecognizer.startListening(intent);
    }

    private void stopListening() {
        if (speechRecognizer != null) speechRecognizer.stopListening();
        isListening = false;
        setStatus("Ready — tap mic", "#4CAF50");
        btnMic.setImageResource(R.drawable.ic_mic);
    }

    // ─────────────────────────────────────────────
    //  COMMAND HANDLER — the brain
    // ─────────────────────────────────────────────
    private void handleCommand(String cmd) {

        // ── OPEN APP
        if (cmd.contains("open")) {
            String appName = cmd.replaceAll("open", "").trim();
            openApp(appName);
        }

        // ── TIME
        else if (cmd.contains("time") || cmd.contains("what time")) {
            String time = new SimpleDateFormat("hh:mm a", Locale.US).format(new Date());
            String reply = "It's " + time;
            speak(reply); appendResponse("JARVIS: " + reply);
        }

        // ── DATE
        else if (cmd.contains("date") || cmd.contains("today")) {
            String date = new SimpleDateFormat("EEEE, MMMM d yyyy", Locale.US).format(new Date());
            String reply = "Today is " + date;
            speak(reply); appendResponse("JARVIS: " + reply);
        }

        // ── SET ALARM
        else if (cmd.contains("alarm") || cmd.contains("wake me")) {
            int[] time = extractTime(cmd);
            if (time != null) {
                setAlarm(time[0], time[1]);
            } else {
                speak("Please say a time, like: set alarm at 7 30 AM");
                appendResponse("JARVIS: Please say a time, like 'set alarm at 7 30 AM'");
            }
        }

        // ── REMIND ME
        else if (cmd.contains("remind") || cmd.contains("reminder")) {
            int[] time = extractTime(cmd);
            if (time != null) {
                setAlarm(time[0], time[1]);
                speak("Reminder set for " + time[0] + " " + time[1] + " minutes");
                appendResponse("JARVIS: Reminder set!");
            } else {
                speak("Say something like: remind me at 6 PM");
                appendResponse("JARVIS: Say 'remind me at 6 PM'");
            }
        }

        // ── PLAY MUSIC
        else if (cmd.contains("play") && (cmd.contains("music") || cmd.contains("song") || cmd.contains("spotify"))) {
            openAppByPackage("com.spotify.music", "Spotify");
        }
        else if (cmd.contains("play youtube") || cmd.contains("play video")) {
            openAppByPackage("com.google.android.youtube", "YouTube");
        }

        // ── CALL
        else if (cmd.contains("call")) {
            String reply = "Calling feature requires contacts permission. Coming soon!";
            speak(reply); appendResponse("JARVIS: " + reply);
        }

        // ── BATTERY
        else if (cmd.contains("battery")) {
            Intent batteryIntent = registerReceiver(null,
                    new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                int level = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                String reply = "Battery is at " + level + " percent";
                speak(reply); appendResponse("JARVIS: " + reply);
            }
        }

        // ── SEARCH GOOGLE
        else if (cmd.contains("search") || cmd.contains("google")) {
            String query = cmd.replaceAll("(search|google|for|about)", "").trim();
            Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)));
            startActivity(i);
            speak("Searching for " + query);
            appendResponse("JARVIS: Searching for " + query);
        }

        // ── GREETING
        else if (cmd.contains("hello") || cmd.contains("hey") || cmd.contains("hi jarvis")) {
            String[] greets = {"Hey! What can I do for you?", "Hello! Ready to help.", "Hey there! What do you need?"};
            String reply = greets[(int)(Math.random() * greets.length)];
            speak(reply); appendResponse("JARVIS: " + reply);
        }

        // ── STOP / EXIT
        else if (cmd.contains("stop") || cmd.contains("exit") || cmd.contains("bye")) {
            speak("Goodbye! JARVIS going offline.");
            appendResponse("JARVIS: Goodbye!");
            handler.postDelayed(this::finish, 2500);
        }

        // ── UNKNOWN
        else {
            speak("I didn't understand that command. Try saying open, time, alarm, or search.");
            appendResponse("JARVIS: Unknown command. Try: open, time, alarm, search.");
        }
    }

    // ─────────────────────────────────────────────
    //  OPEN APP — searches installed apps by name
    // ─────────────────────────────────────────────
    private void openApp(String appName) {
        // First try hardcoded packages (most reliable)
        String pkg = getKnownPackage(appName);
        if (pkg != null) {
            openAppByPackage(pkg, appName);
            return;
        }

        // Then search all installed apps by label
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);

        for (ResolveInfo info : apps) {
            String label = info.loadLabel(pm).toString().toLowerCase();
            if (label.contains(appName.toLowerCase()) || appName.toLowerCase().contains(label)) {
                Intent launchIntent = pm.getLaunchIntentForPackage(
                        info.activityInfo.packageName);
                if (launchIntent != null) {
                    startActivity(launchIntent);
                    speak("Opening " + info.loadLabel(pm).toString());
                    appendResponse("JARVIS: Opening " + info.loadLabel(pm).toString());
                    return;
                }
            }
        }

        speak("App not found. Make sure " + appName + " is installed.");
        appendResponse("JARVIS: '" + appName + "' not found on this device.");
    }

    private void openAppByPackage(String packageName, String label) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(packageName);
        if (intent != null) {
            startActivity(intent);
            speak("Opening " + label);
            appendResponse("JARVIS: Opening " + label);
        } else {
            speak(label + " is not installed.");
            appendResponse("JARVIS: " + label + " not installed.");
        }
    }

    // ── Known package map (most reliable)
    private String getKnownPackage(String appName) {
        appName = appName.toLowerCase().trim();
        if (appName.contains("youtube"))    return "com.google.android.youtube";
        if (appName.contains("whatsapp"))   return "com.whatsapp";
        if (appName.contains("instagram"))  return "com.instagram.android";
        if (appName.contains("facebook"))   return "com.facebook.katana";
        if (appName.contains("chrome"))     return "com.android.chrome";
        if (appName.contains("gmail"))      return "com.google.android.gm";
        if (appName.contains("maps"))       return "com.google.android.apps.maps";
        if (appName.contains("spotify"))    return "com.spotify.music";
        if (appName.contains("snapchat"))   return "com.snapchat.android";
        if (appName.contains("twitter") || appName.contains("x app")) return "com.twitter.android";
        if (appName.contains("tiktok"))     return "com.zhiliaoapp.musically";
        if (appName.contains("telegram"))   return "org.telegram.messenger";
        if (appName.contains("netflix"))    return "com.netflix.mediaclient";
        if (appName.contains("camera"))     return "com.android.camera2";
        if (appName.contains("calculator")) return "com.android.calculator2";
        if (appName.contains("settings"))   return "com.android.settings";
        if (appName.contains("clock"))      return "com.android.deskclock";
        if (appName.contains("photos"))     return "com.google.android.apps.photos";
        if (appName.contains("play store")) return "com.android.vending";
        if (appName.contains("linkedin"))   return "com.linkedin.android";
        if (appName.contains("discord"))    return "com.discord";
        if (appName.contains("zoom"))       return "us.zoom.videomeetings";
        return null;
    }

    // ─────────────────────────────────────────────
    //  SET ALARM
    // ─────────────────────────────────────────────
    private void setAlarm(int hour, int minute) {
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
            intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS Alarm");
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            startActivity(intent);
            speak("Alarm set for " + hour + " " + (minute == 0 ? "o'clock" : minute));
            appendResponse("JARVIS: Alarm set for " + hour + ":" + String.format("%02d", minute));
        } catch (Exception e) {
            speak("Could not set alarm. Opening clock app.");
            appendResponse("JARVIS: Alarm failed. Open clock manually.");
        }
    }

    // ── Extract hour/minute from command like "at 7 30" or "at 6 PM"
    private int[] extractTime(String cmd) {
        try {
            // Pattern: digits like "7 30" or "6" (with optional AM/PM)
            boolean pm = cmd.contains("pm") || cmd.contains("p m");
            boolean am = cmd.contains("am") || cmd.contains("a m");

            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d{1,2})\\s*(\\d{2})?");
            java.util.regex.Matcher m = p.matcher(cmd);
            if (m.find()) {
                int hour = Integer.parseInt(m.group(1));
                int minute = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
                if (pm && hour < 12) hour += 12;
                if (am && hour == 12) hour = 0;
                return new int[]{hour, minute};
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ─────────────────────────────────────────────
    //  TTS + UI HELPERS
    // ─────────────────────────────────────────────
    private void speak(String text) {
        if (ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_" + System.currentTimeMillis());
        }
    }

    private void appendResponse(String text) {
        String current = tvResponse.getText().toString();
        tvResponse.setText(current.isEmpty() ? text : current + "\n\n" + text);
        // Scroll to bottom
        final Layout layout = tvResponse.getLayout();
        if (layout != null) {
            int scrollDelta = layout.getLineBottom(tvResponse.getLineCount() - 1)
                    - tvResponse.getScrollY() - tvResponse.getHeight();
            if (scrollDelta > 0) tvResponse.scrollBy(0, scrollDelta);
        }
    }

    private void setStatus(String text, String hexColor) {
        tvStatus.setText(text);
        tvStatus.setTextColor(android.graphics.Color.parseColor(hexColor));
    }

    // ─────────────────────────────────────────────
    //  CLEANUP
    // ─────────────────────────────────────────────
    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speechRecognizer != null) speechRecognizer.destroy();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == 1 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Mic permission granted!", Toast.LENGTH_SHORT).show();
        }
    }
}
