package com.cassimtv.remote;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;

public class TvAccessibilityService extends AccessibilityService {
    private static volatile TvAccessibilityService instance;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean performRemoteAction(String action) {
        TvAccessibilityService service = instance;
        if (service == null) return false;
        switch (action) {
            case "HOME":
                return service.performGlobalAction(GLOBAL_ACTION_HOME);
            case "BACK":
                return service.performGlobalAction(GLOBAL_ACTION_BACK);
            case "POWER":
                return service.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
            case "DPAD_UP":
                return service.moveFocus(0, -1);
            case "DPAD_DOWN":
                return service.moveFocus(0, 1);
            case "DPAD_LEFT":
                return service.moveFocus(-1, 0);
            case "DPAD_RIGHT":
                return service.moveFocus(1, 0);
            case "OK":
                return service.clickFocused();
            default:
                return false;
        }
    }

    private boolean moveFocus(int dx, int dy) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            List<AccessibilityNodeInfo> nodes = new ArrayList<>();
            collectFocusable(root, nodes);
            AccessibilityNodeInfo current = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if (current == null) current = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            Rect currentRect = new Rect();
            if (current != null) {
                current.getBoundsInScreen(currentRect);
            } else {
                root.getBoundsInScreen(currentRect);
                AccessibilityNodeInfo initial = null;
                double initialScore = Double.MAX_VALUE;
                int centerX = currentRect.centerX();
                int centerY = currentRect.centerY();
                for (AccessibilityNodeInfo node : nodes) {
                    Rect rect = new Rect();
                    node.getBoundsInScreen(rect);
                    double score = Math.hypot(rect.centerX() - centerX, rect.centerY() - centerY);
                    if (score < initialScore) {
                        initialScore = score;
                        initial = node;
                    }
                }
                if (initial != null) {
                    initial.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                    return initial.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                            || initial.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                }
            }

            AccessibilityNodeInfo best = null;
            double bestScore = Double.MAX_VALUE;
            int cx = currentRect.centerX();
            int cy = currentRect.centerY();
            for (AccessibilityNodeInfo node : nodes) {
                if (current != null && node.equals(current)) continue;
                Rect rect = new Rect();
                node.getBoundsInScreen(rect);
                int vx = rect.centerX() - cx;
                int vy = rect.centerY() - cy;
                int primary = dx != 0 ? vx * dx : vy * dy;
                if (primary <= 0) continue;
                int cross = dx != 0 ? Math.abs(vy) : Math.abs(vx);
                double score = primary + cross * 2.2 + cross * cross / (double) Math.max(1, primary);
                if (score < bestScore) {
                    bestScore = score;
                    best = node;
                }
            }
            if (best == null && current == null && !nodes.isEmpty()) best = nodes.get(0);
            if (best == null) return false;
            best.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            return best.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    || best.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        } finally {
            root.recycle();
        }
    }

    private void collectFocusable(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out) {
        if (node == null) return;
        if (node.isVisibleToUser() && (node.isFocusable() || node.isClickable())) {
            out.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectFocusable(child, out);
        }
    }

    private boolean clickFocused() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if (focused == null) focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            return focused != null && focused.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } finally {
            root.recycle();
        }
    }
}
