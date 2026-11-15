package app.pleos.stop30;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import ai.pleos.playground.stt.SpeechToText;
import ai.pleos.playground.stt.constant.ErrorType;
import ai.pleos.playground.stt.constant.Mode;
import ai.pleos.playground.stt.listener.OnInitializeListener;
import ai.pleos.playground.stt.listener.ResultListener;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;

/** Feeds the bundled synthetic speech to the Pleos on-device engine in 100 ms PCM frames. */
final class ExampleSpeech implements ResultListener, OnInitializeListener {
    private static final int FRAME_BYTES = 3200;
    private final Handler ui;
    private final SpeechToText stt;
    private final byte[] pcm;
    private final Consumer<String> complete;
    private final Runnable timeout = () -> fail("timeout");
    private int offset;
    private boolean active;

    ExampleSpeech(Context context, Handler ui, Consumer<String> complete) throws IOException {
        this.ui = ui;
        this.complete = complete;
        try (var input = context.getAssets().open("prompt.pcm")) {
            pcm = input.readAllBytes();
        }
        stt = new SpeechToText(context, Mode.ON_DEVICE);
    }

    void start() {
        active = true;
        ui.postDelayed(timeout, 9000);
        stt.addListener(this);
        stt.initialize(this);
    }

    @Override public void onSuccess() {
        ui.post(() -> {
            if (!active) return;
            ui.removeCallbacks(timeout);
            try {
                stt.setLocale(Locale.KOREA);
                feed();
            } catch (RuntimeException error) {
                fail(error.toString());
            }
        });
    }

    private void feed() {
        if (!active) return;
        try {
            if (offset == pcm.length) {
                stt.completeAudioSend();
                ui.postDelayed(timeout, 13000);
                return;
            }
            int end = Math.min(offset + FRAME_BYTES, pcm.length);
            stt.sendAudio(Arrays.copyOfRange(pcm, offset, end));
            offset = end;
            ui.postDelayed(this::feed, 100);
        } catch (RuntimeException error) {
            fail(error.toString());
        }
    }

    void cancel() {
        if (!active) return;
        active = false;
        ui.removeCallbacks(timeout);
        stt.removeListener(this);
        stt.release();
    }

    private void fail(String error) {
        ui.post(() -> {
            if (!active) return;
            Log.w(StopActivity.TAG, "EXAMPLE_STT_FAILURE " + error);
            cancel();
            complete.accept(null);
        });
    }

    @Override public void onUpdated(String text, boolean finalValue) {
        if (!finalValue || text == null || text.isBlank()) return;
        ui.post(() -> {
            if (!active) return;
            cancel();
            complete.accept(text);
        });
    }

    @Override public void onFailure(ErrorType error) { fail(error.toString()); }
    @Override public void onError(ErrorType error) { fail(error.toString()); }
    @Override public void onUpdatedEpdData(long from, long to) {}
    @Override public void onStartedRecognition() {}
    @Override public void onEndedRecognition() {}
    @Override public void onReady() {}
    @Override public void onUpdatedRms(double value) {}
}
