package mulin.tvdy;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.widget.FrameLayout;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * TV DY: Douyin web remote-controller compatibility shell for TV devices.
 */
public class MainActivity extends Activity {
    private static final String DEFAULT_HOME_URL = "https://www.douyin.com/?recommend=1&from_nav=1";
    private static final int BACK_PRESS_INTERVAL = 2000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private LinearLayout hintPanel;
    private TextView hintLastAction;
    private FrameLayout wheelPanel;
    private TextView wheelCenter;
    private TextView[] wheelItems;
    private int wheelSelectedIndex = 0;
    private long lastBackPressTime = 0;

    private final Runnable hideHintRunnable = () -> {
        if (hintPanel == null) return;
        hintPanel.animate()
                .alpha(0f)
                .setDuration(140)
                .withEndAction(() -> hintPanel.setVisibility(View.GONE))
                .start();
    };

    private final Runnable hideWheelRunnable = () -> hideWheel(true);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        enableImmersiveMode();
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        setupWebView();
        createHintPanel();
        createWheelPanel();
        showHint("\u51c6\u5907\u5c31\u7eea", true);
        webView.loadUrl(DEFAULT_HOME_URL);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enableImmersiveMode();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    @SuppressWarnings("deprecation")
    private void enableImmersiveMode() {
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        getWindow().getDecorView().setSystemUiVisibility(uiOptions);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36");

        webView.setOnTouchListener(null);
        webView.setOnKeyListener(null);
        webView.setOnGenericMotionListener(null);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                redirectHomeIfNeeded(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                redirectHomeIfNeeded(view, url);
                webView.requestFocus();
            }
        });
    }

    private boolean redirectHomeIfNeeded(WebView view, String url) {
        if (!isDouyinUrl(url)) return false;
        if (url != null && (url.contains("recommend=1") || url.contains("discover") || url.contains("follow"))) {
            return true;
        }
        view.loadUrl(DEFAULT_HOME_URL);
        return true;
    }

    private boolean isDouyinUrl(String url) {
        if (url == null) return false;
        try {
            String host = Uri.parse(url).getHost();
            return host != null && (host.equals("douyin.com") || host.endsWith(".douyin.com"));
        } catch (Exception ignored) {
            return url.toLowerCase().contains("douyin.com");
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && handleRemoteKey(event.getKeyCode())) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean handleRemoteKey(int keyCode) {
        if (isWheelVisible()) {
            return handleWheelKey(keyCode);
        }

        RemoteAction action = mapPrimaryAction(keyCode);
        if (action == null) return false;

        showHint(action.label, false);
        if (action.webKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
            sendWebKey(action.webKeyCode, action.metaState);
        }
        return true;
    }

    private RemoteAction mapPrimaryAction(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return RemoteAction.web("\u4e0a\u4e00\u4e2a\u89c6\u9891", KeyEvent.KEYCODE_DPAD_UP);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return RemoteAction.web("\u4e0b\u4e00\u4e2a\u89c6\u9891", KeyEvent.KEYCODE_DPAD_DOWN);
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                return RemoteAction.web("\u6682\u505c / \u64ad\u653e", KeyEvent.KEYCODE_SPACE);
            case KeyEvent.KEYCODE_DPAD_LEFT:
                showWheel(-1);
                return RemoteAction.local("\u6253\u5f00\u529f\u80fd\u8f6e\u76d8");
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                showWheel(1);
                return RemoteAction.local("\u6253\u5f00\u529f\u80fd\u8f6e\u76d8");
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_M:
                showHint("\u663e\u793a\u9065\u63a7\u6307\u5357", false);
                return RemoteAction.local("\u663e\u793a\u9065\u63a7\u6307\u5357");
            case KeyEvent.KEYCODE_BACK:
                long now = System.currentTimeMillis();
                if (now - lastBackPressTime < BACK_PRESS_INTERVAL) {
                    finish();
                } else {
                    Toast.makeText(this, "\u518d\u6309\u4e00\u6b21\u8fd4\u56de\u9000\u51fa", Toast.LENGTH_SHORT).show();
                    lastBackPressTime = now;
                }
                return RemoteAction.local("\u8fd4\u56de / \u9000\u51fa");
            default:
                return null;
        }
    }

    private boolean handleWheelKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                moveWheelSelection(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                moveWheelSelection(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                sendWebKey(KeyEvent.KEYCODE_SPACE, 0);
                showHint("\u64ad\u653e / \u6682\u505c", false);
                refreshWheelTimeout();
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_M:
                hideWheel(false);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                sendWebKey(KeyEvent.KEYCODE_DPAD_UP, 0);
                showHint("\u4e0a\u4e00\u4e2a\u89c6\u9891", false);
                hideWheel(false);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                sendWebKey(KeyEvent.KEYCODE_DPAD_DOWN, 0);
                showHint("\u4e0b\u4e00\u4e2a\u89c6\u9891", false);
                hideWheel(false);
                return true;
            default:
                refreshWheelTimeout();
                return true;
        }
    }

    private void sendWebKey(int keyCode, int metaState) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                sendDocumentShortcut("ArrowUp", "ArrowUp", 38);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                sendDocumentShortcut("ArrowDown", "ArrowDown", 40);
                break;
            case KeyEvent.KEYCODE_SPACE:
                sendNativeWebKey(KeyEvent.KEYCODE_SPACE, metaState);
                break;
            case KeyEvent.KEYCODE_Z:
                sendDocumentShortcut("z", "KeyZ", 90);
                break;
            case KeyEvent.KEYCODE_C:
                sendDocumentShortcut("c", "KeyC", 67);
                break;
            case KeyEvent.KEYCODE_G:
                sendDocumentShortcut("g", "KeyG", 71);
                break;
            case KeyEvent.KEYCODE_X:
                sendDocumentShortcut("x", "KeyX", 88);
                break;
            case KeyEvent.KEYCODE_J:
                sendDocumentShortcut("j", "KeyJ", 74);
                break;
            case KeyEvent.KEYCODE_B:
                sendDocumentShortcut("b", "KeyB", 66);
                break;
            case KeyEvent.KEYCODE_Y:
                sendDocumentShortcut("y", "KeyY", 89);
                break;
            default:
                sendNativeWebKey(keyCode, metaState);
                break;
        }
    }

    private void sendNativeWebKey(int keyCode, int metaState) {
        webView.requestFocus();
        long downTime = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(
                downTime,
                downTime,
                KeyEvent.ACTION_DOWN,
                keyCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                0,
                InputDevice.SOURCE_KEYBOARD
        );
        KeyEvent up = new KeyEvent(
                downTime,
                SystemClock.uptimeMillis() + 24,
                KeyEvent.ACTION_UP,
                keyCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                0,
                InputDevice.SOURCE_KEYBOARD
        );
        webView.dispatchKeyEvent(down);
        webView.dispatchKeyEvent(up);
    }

    private void sendDouyinShortcut(String key, String code, int keyCode) {
        webView.requestFocus();
        String js = "(function(){"
                + "function fire(type){"
                + "var e=new KeyboardEvent(type,{key:'" + key + "',code:'" + code + "',keyCode:" + keyCode + ",which:" + keyCode + ",bubbles:true,cancelable:true});"
                + "try{Object.defineProperty(e,'keyCode',{get:function(){return " + keyCode + ";}});}catch(err){}"
                + "try{Object.defineProperty(e,'which',{get:function(){return " + keyCode + ";}});}catch(err){}"
                + "try{Object.defineProperty(e,'code',{get:function(){return '" + code + "';}});}catch(err){}"
                + "try{Object.defineProperty(e,'key',{get:function(){return '" + key + "';}});}catch(err){}"
                + "var t=document.activeElement||document.body||document.documentElement;"
                + "try{t.dispatchEvent(e);}catch(err){}"
                + "try{document.dispatchEvent(e);}catch(err){}"
                + "try{window.dispatchEvent(e);}catch(err){}"
                + "}"
                + "fire('keydown');setTimeout(function(){fire('keyup');},30);"
                + "})();";
        webView.evaluateJavascript(js, null);
    }

    private void sendDocumentShortcut(String key, String code, int keyCode) {
        webView.requestFocus();
        String js = "document.dispatchEvent(new KeyboardEvent('keydown',{"
                + "key:'" + key + "',"
                + "code:'" + code + "',"
                + "keyCode:" + keyCode + ","
                + "which:" + keyCode + ","
                + "bubbles:true,"
                + "cancelable:true"
                + "}));"
                + "document.dispatchEvent(new KeyboardEvent('keyup',{"
                + "key:'" + key + "',"
                + "code:'" + code + "',"
                + "keyCode:" + keyCode + ","
                + "which:" + keyCode + ","
                + "bubbles:true,"
                + "cancelable:true"
                + "}));";
        webView.evaluateJavascript(js, null);
    }

    private void sendWheelShortcut(int keyCode) {
        sendWebKey(keyCode, 0);
    }

    private void createWheelPanel() {
        wheelPanel = new FrameLayout(this);
        wheelPanel.setVisibility(View.GONE);
        wheelPanel.setAlpha(0f);
        wheelPanel.setClickable(false);
        wheelPanel.setFocusable(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(118, 6, 10, 18));
        bg.setStroke(dp(1), Color.argb(46, 255, 255, 255));
        wheelPanel.setBackground(bg);

        int screenShort = Math.min(
                getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels
        );
        int size = Math.min(dp(340), Math.max(dp(260), (int) (screenShort * 0.36f)));
        int center = size / 2;
        int radius = (int) (size * 0.38f);
        int itemWidth = Math.max(dp(62), (int) (size * 0.21f));
        int itemHeight = Math.max(dp(30), (int) (size * 0.085f));

        wheelItems = new TextView[WHEEL_ACTIONS.length];
        for (int i = 0; i < WHEEL_ACTIONS.length; i++) {
            TextView item = wheelItemText(WHEEL_ACTIONS[i].shortLabel);
            double angle = -Math.PI / 2 + (Math.PI * 2 * i / WHEEL_ACTIONS.length);
            int x = center + (int) (Math.cos(angle) * radius) - itemWidth / 2;
            int y = center + (int) (Math.sin(angle) * radius) - itemHeight / 2;
            FrameLayout.LayoutParams itemParams = new FrameLayout.LayoutParams(itemWidth, itemHeight);
            itemParams.leftMargin = x;
            itemParams.topMargin = y;
            wheelPanel.addView(item, itemParams);
            wheelItems[i] = item;
        }

        wheelCenter = wheelItemText("");
        wheelCenter.setTextSize(13);
        wheelCenter.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams((int) (size * 0.30f), (int) (size * 0.15f));
        centerParams.gravity = Gravity.CENTER;
        wheelPanel.addView(wheelCenter, centerParams);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.gravity = Gravity.CENTER;
        ((ViewGroup) getWindow().getDecorView()).addView(wheelPanel, params);
        updateWheelSelection();
    }

    private TextView wheelItemText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(10.5f);
        view.setTextColor(Color.argb(235, 255, 255, 255));
        view.setIncludeFontPadding(false);
        return view;
    }

    private boolean isWheelVisible() {
        return wheelPanel != null && wheelPanel.getVisibility() == View.VISIBLE;
    }

    private void showWheel(int direction) {
        if (wheelPanel == null) return;
        if (wheelPanel.getVisibility() != View.VISIBLE) {
            wheelSelectedIndex = 0;
            updateWheelSelection();
            wheelPanel.setVisibility(View.VISIBLE);
            wheelPanel.animate().cancel();
            wheelPanel.setScaleX(0.94f);
            wheelPanel.setScaleY(0.94f);
            wheelPanel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(105).start();
            refreshWheelTimeout();
        } else {
            moveWheelSelection(direction);
        }
    }

    private void hideWheel(boolean executeSelected) {
        if (wheelPanel == null || wheelPanel.getVisibility() != View.VISIBLE) return;
        handler.removeCallbacks(hideWheelRunnable);
        final int selected = wheelSelectedIndex;
        wheelPanel.animate()
                .alpha(0f)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(95)
                .withEndAction(() -> {
                    wheelPanel.setVisibility(View.GONE);
                    if (executeSelected && selected != 0) {
                        executeWheelAction(selected);
                    }
                    wheelSelectedIndex = 0;
                    updateWheelSelection();
                })
                .start();
    }

    private void refreshWheelTimeout() {
        handler.removeCallbacks(hideWheelRunnable);
        handler.postDelayed(hideWheelRunnable, 1500);
    }

    private void moveWheelSelection(int delta) {
        wheelSelectedIndex = (wheelSelectedIndex + delta + WHEEL_ACTIONS.length) % WHEEL_ACTIONS.length;
        updateWheelSelection();
        refreshWheelTimeout();
    }

    private void executeWheelAction(int index) {
        WheelAction action = WHEEL_ACTIONS[index];
        if (action.webKeyCode == KeyEvent.KEYCODE_UNKNOWN) {
            showHint(action.label, false);
            return;
        }
        sendWheelShortcut(action.webKeyCode);
        showHint(action.label, false);
    }

    private void updateWheelSelection() {
        if (wheelItems == null || wheelCenter == null) return;
        for (int i = 0; i < wheelItems.length; i++) {
            boolean selected = i == wheelSelectedIndex;
            TextView item = wheelItems[i];
            if (item == null) continue;
            item.setTextColor(selected ? Color.WHITE : Color.argb(220, 255, 255, 255));
            item.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            item.setTextSize(selected ? 11.5f : 10.5f);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(18));
            bg.setColor(selected ? Color.argb(224, 0, 121, 251) : Color.argb(54, 255, 255, 255));
            bg.setStroke(dp(1), selected ? Color.argb(160, 160, 210, 255) : Color.argb(34, 255, 255, 255));
            item.setBackground(bg);
        }
        WheelAction action = WHEEL_ACTIONS[wheelSelectedIndex];
        wheelCenter.setText(action.label);
        wheelCenter.setBackground(null);
    }

    private void createHintPanel() {
        hintPanel = new LinearLayout(this);
        hintPanel.setOrientation(LinearLayout.VERTICAL);
        hintPanel.setPadding(dp(14), dp(10), dp(14), dp(10));
        hintPanel.setClickable(false);
        hintPanel.setFocusable(false);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(112, 8, 12, 20));
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), Color.argb(34, 255, 255, 255));
        hintPanel.setBackground(background);

        hintPanel.addView(hintText("\u4e0a\u952e  \u4e0a\u4e00\u6761\u89c6\u9891", 13, false, Color.WHITE));
        hintPanel.addView(hintText("\u4e0b\u952e  \u4e0b\u4e00\u6761\u89c6\u9891", 13, false, Color.WHITE));
        hintPanel.addView(hintText("OK    \u64ad\u653e / \u6682\u505c", 13, false, Color.WHITE));
        hintPanel.addView(hintText("\u5de6\u53f3  \u529f\u80fd\u8f6e\u76d8", 13, false, Color.WHITE));
        hintLastAction = hintText("\u5c31\u7eea", 12, true, Color.argb(240, 0, 229, 255));
        hintPanel.addView(hintLastAction);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        params.rightMargin = dp(22);
        ((ViewGroup) getWindow().getDecorView()).addView(hintPanel, params);
    }

    private TextView hintText(String text, int sp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setIncludeFontPadding(false);
        view.setPadding(0, dp(4), 0, dp(4));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void showHint(String action, boolean initial) {
        if (hintPanel == null) return;
        hintLastAction.setText(action);
        hintPanel.setVisibility(View.VISIBLE);
        hintPanel.animate().cancel();
        hintPanel.setAlpha(1f);
        handler.removeCallbacks(hideHintRunnable);
        handler.postDelayed(hideHintRunnable, initial ? 3600 : 1200);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final WheelAction[] WHEEL_ACTIONS = new WheelAction[]{
            // Default / neutral point. Right side is interaction, left side is playback and page functions.
            new WheelAction("\u65e0", "\u65e0", KeyEvent.KEYCODE_UNKNOWN),

            // Right side: interaction. High-frequency actions stay closer to "none".
            new WheelAction("\u70b9\u8d5e", "\u8d5e / \u53d6\u6d88\u8d5e", KeyEvent.KEYCODE_Z),
            new WheelAction("\u6536\u85cf", "\u6536\u85cf / \u53d6\u6d88\u6536\u85cf", KeyEvent.KEYCODE_C),
            new WheelAction("\u5173\u6ce8", "\u5173\u6ce8 / \u53d6\u6d88\u5173\u6ce8", KeyEvent.KEYCODE_G),
            new WheelAction("\u8bc4\u8bba", "\u8bc4\u8bba", KeyEvent.KEYCODE_X),
            // Left side: playback and functional controls. Reverse direction from none reaches these first.
            new WheelAction("\u5f39\u5e55", "\u5f39\u5e55\u5f00\u5173", KeyEvent.KEYCODE_B),
            new WheelAction("\u6e05\u5c4f", "\u6e05\u5c4f", KeyEvent.KEYCODE_J),
            new WheelAction("\u5168\u5c4f", "\u7f51\u9875\u5185\u5168\u5c4f", KeyEvent.KEYCODE_Y)
    };

    private static final class WheelAction {
        final String shortLabel;
        final String label;
        final int webKeyCode;

        WheelAction(String shortLabel, String label, int webKeyCode) {
            this.shortLabel = shortLabel;
            this.label = label;
            this.webKeyCode = webKeyCode;
        }
    }

    private static final class RemoteAction {
        final String label;
        final int webKeyCode;
        final int metaState;

        private RemoteAction(String label, int webKeyCode, int metaState) {
            this.label = label;
            this.webKeyCode = webKeyCode;
            this.metaState = metaState;
        }

        static RemoteAction web(String label, int webKeyCode) {
            return new RemoteAction(label, webKeyCode, 0);
        }

        static RemoteAction local(String label) {
            return new RemoteAction(label, KeyEvent.KEYCODE_UNKNOWN, 0);
        }
    }
}
