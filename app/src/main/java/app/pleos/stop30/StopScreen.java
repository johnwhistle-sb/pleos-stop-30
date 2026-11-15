package app.pleos.stop30;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class StopScreen {
    private static final int PAPER = 0xfffbf9f5;
    private static final int INK = 0xff1c1c1c;
    private static final int FOREST = 0xff145c4e;
    private static final int MIST = 0xffe6efe9;
    private static final int HAIRLINE = 0xffe0ded6;
    private static final int QUIET = 0xff505953;
    private final Context context;
    private final float density;
    private final float scale;
    private final boolean wide;
    final View root;
    final TextView gear;
    final TextView eta;
    final TextView recommendationLabel;
    final TextView place;
    final TextView placeDetail;
    final View metrics;
    final TextView entry;
    final TextView arrival;
    final TextView reasonTitle;
    final TextView reason;
    final TextView change;
    final TextView notice;
    final TextView source;
    final TextView transcript;
    final Button primary;
    final View demo;
    final Button reset;
    final Button route;
    final Button schedule;

    StopScreen(Context context) {
        this.context = context;
        var config = context.getResources().getConfiguration();
        wide = config.screenWidthDp >= 860;
        scale = config.screenWidthDp > 1310 ? 1.22f : 1f;
        density = context.getResources().getDisplayMetrics().density * scale;
        int gutter = wide ? 32 : 20;

        var scroll = new ScrollView(context);
        scroll.setBackgroundColor(PAPER);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(gutter), dp(18), dp(gutter), dp(18));
        var frame = new FrameLayout(context);
        scroll.addView(frame, new ScrollView.LayoutParams(-1, -2));
        var page = column();
        int contentWidth = config.screenWidthDp > 1310 ? Math.round(dp(1240) / scale) : -1;
        frame.addView(page, new FrameLayout.LayoutParams(contentWidth, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        root = scroll;

        var masthead = row();
        masthead.setGravity(Gravity.CENTER_VERTICAL);
        var appName = label("가는 길 30분", 19, INK, true);
        masthead.addView(appName, new LinearLayout.LayoutParams(0, -2, 1));
        gear = label("차량 상태 확인 중", 15, FOREST, true);
        gear.setPadding(dp(12), dp(9), dp(12), dp(9));
        gear.setBackground(surface(MIST, MIST, 7));
        masthead.addView(gear);
        append(page, masthead, 0);
        append(page, rule(), 15, 1);

        var destination = label("분당구청 · 18:00까지", wide ? 42 : 31, INK, true);
        destination.setLetterSpacing(-0.025f);
        destination.setAccessibilityHeading(true);
        append(page, destination, wide ? 18 : 16);
        eta = label("판교 출발 · 시연 경로", 17, QUIET, false);
        append(page, eta, 8);

        var panels = new LinearLayout(context);
        panels.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        var recommendation = column();
        stylePanel(recommendation, Color.WHITE, HAIRLINE);
        recommendationLabel = label("경유지 추천", 17, FOREST, true);
        append(recommendation, recommendationLabel, 0);
        place = label("가는 길에 30분 들를 곳", wide ? 37 : 27, INK, true);
        place.setLetterSpacing(-0.025f);
        place.setAccessibilityHeading(true);
        append(recommendation, place, 9);
        placeDetail = label("주차할 수 있는 실내 장소를 찾아요.", 17, QUIET, false);
        append(recommendation, placeDetail, 9);

        var times = row();
        times.setBackground(surface(MIST, MIST, 9));
        times.setPadding(dp(15), dp(13), dp(15), dp(13));
        entry = label("", wide ? 28 : 25, FOREST, true);
        arrival = label("", wide ? 28 : 25, FOREST, true);
        times.addView(timeCell("입장 예상", entry), new LinearLayout.LayoutParams(0, -2, 1));
        var vertical = new View(context);
        vertical.setBackgroundColor(0xffbdd1c6);
        var verticalLayout = new LinearLayout.LayoutParams(dp(1), -1);
        verticalLayout.setMargins(dp(10), 0, dp(15), 0);
        times.addView(vertical, verticalLayout);
        times.addView(timeCell("분당구청 도착 예상", arrival), new LinearLayout.LayoutParams(0, -2, 1));
        metrics = times;
        append(recommendation, metrics, 19);

        var contextPane = column();
        stylePanel(contextPane, 0xfff2efe6, 0xffe9e2d4);
        reasonTitle = label("추천 기준", 17, FOREST, true);
        append(contextPane, reasonTitle, 0);
        reason = label("입장·주차 마감까지 도착하고, 실내에서 30분 머문 뒤 분당구청에 도착할 수 있는지 확인합니다.",
                wide ? 21 : 18, INK, true);
        append(contextPane, reason, 11);
        change = label("", 16, QUIET, false);
        append(contextPane, change, 10);

        if (wide) {
            panels.addView(recommendation, new LinearLayout.LayoutParams(0, -1, 3));
            var paneLayout = new LinearLayout.LayoutParams(0, -1, 2);
            paneLayout.setMarginStart(dp(16));
            panels.addView(contextPane, paneLayout);
        } else {
            append(panels, recommendation, 0);
            append(panels, contextPane, 12);
        }
        append(page, panels, wide ? 20 : 15);

        notice = label("", 17, INK, false);
        notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        append(page, notice, 14);
        primary = action("예시 음성으로 시작", true);
        append(page, primary, 10);

        var demoArea = column();
        append(demoArea, rule(), 0, 1);
        append(demoArea, label("시연 조작", 15, QUIET, true), 13);
        var demoActions = new LinearLayout(context);
        demoActions.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        reset = action("예시 음성 다시 시작", false);
        route = action("경로 변경 시연", false);
        schedule = action("마감 변경 시연", false);
        addDemoButton(demoActions, reset, true);
        addDemoButton(demoActions, route, false);
        addDemoButton(demoActions, schedule, false);
        append(demoArea, demoActions, 7);
        transcript = label("", 14, QUIET, false);
        append(demoArea, transcript, 9);
        demo = demoArea;
        append(page, demo, 17);
        source = label("2026.11.15 운영 시간 기준 · 주차 여유는 현장에서 확인하세요.", 14, QUIET, false);
        append(page, source, 12);
    }

    private LinearLayout timeCell(String heading, TextView value) {
        var cell = column();
        append(cell, label(heading, 14, QUIET, false), 0);
        append(cell, value, 5);
        return cell;
    }

    private void stylePanel(LinearLayout view, int fill, int stroke) {
        int pad = dp(wide ? 23 : 17);
        view.setPadding(pad, pad, pad, pad);
        view.setBackground(surface(fill, stroke, 13));
    }

    private TextView label(String text, int size, int color, boolean bold) {
        var view = new TextView(context);
        view.setText(text);
        view.setTextSize(size * scale);
        view.setLineSpacing(dp(2), 1.11f);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private Button action(String text, boolean prominent) {
        var button = new Button(context);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize((prominent ? 20 : 16) * scale);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(prominent ? 58 : 48));
        button.setPadding(dp(14), dp(8), dp(14), dp(8));
        button.setStateListAnimator(null);
        int fill = prominent ? FOREST : PAPER;
        int textColor = prominent ? Color.WHITE : FOREST;
        int[] disabled = {-android.R.attr.state_enabled};
        var background = new android.graphics.drawable.StateListDrawable();
        background.addState(disabled, surface(0xffe8e8e1, 0xffe8e8e1, 8));
        background.addState(new int[]{android.R.attr.state_focused}, surface(fill, INK, 8, 3));
        background.addState(new int[0], surface(fill, prominent ? FOREST : 0xff9ab4a8, 8));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x321c4b39), background, null));
        button.setTextColor(new ColorStateList(new int[][]{disabled, new int[0]}, new int[]{QUIET, textColor}));
        return button;
    }

    private void addDemoButton(LinearLayout actions, Button button, boolean first) {
        if (!wide) {
            append(actions, button, first ? 0 : 6);
            return;
        }
        var layout = new LinearLayout.LayoutParams(0, -2, 1);
        if (!first) layout.setMarginStart(dp(9));
        actions.addView(button, layout);
    }

    private View rule() {
        var view = new View(context);
        view.setBackgroundColor(HAIRLINE);
        return view;
    }

    private GradientDrawable surface(int fill, int stroke, int radius) { return surface(fill, stroke, radius, 1); }

    private GradientDrawable surface(int fill, int stroke, int radius, int strokeWidth) {
        var shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setStroke(dp(strokeWidth), stroke);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private LinearLayout column() {
        var view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout row() {
        var view = new LinearLayout(context);
        view.setOrientation(LinearLayout.HORIZONTAL);
        return view;
    }

    private void append(LinearLayout parent, View view, int top) { append(parent, view, top, -2); }

    private void append(LinearLayout parent, View view, int top, int height) {
        var layout = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height < 0 ? height : dp(height));
        layout.topMargin = dp(top);
        parent.addView(view, layout);
    }

    private int dp(int value) { return Math.round(value * density); }
}
