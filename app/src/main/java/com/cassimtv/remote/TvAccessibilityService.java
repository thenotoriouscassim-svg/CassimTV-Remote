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
            case "HOME": return service.performGlobalAction(GLOBAL_ACTION_HOME);
            case "BACK": return service.performGlobalAction(GLOBAL_ACTION_BACK);
            case "POWER": return service.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
            case "DPAD_UP": return service.moveFocus(0, -1);
            case "DPAD_DOWN": return service.moveFocus(0, 1);
            case "DPAD_LEFT": return service.moveFocus(-1, 0);
            case "DPAD_RIGHT": return service.moveFocus(1, 0);
            case "OK": return service.clickFocused();
            default: return false;
        }
    }

    private boolean moveFocus(int dx, int dy) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        try {
            collectLeafInteractive(root, nodes);
            if (nodes.isEmpty()) return scrollInDirection(root, dx, dy);

            AccessibilityNodeInfo current = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (current == null) current = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            Rect origin = new Rect();
            if (current != null) {
                current.getBoundsInScreen(origin);
                if (!containsNode(nodes, current)) {
                    AccessibilityNodeInfo nearest = nearestTo(nodes, origin.centerX(), origin.centerY());
                    if (nearest != null) nearest.getBoundsInScreen(origin);
                }
            } else {
                root.getBoundsInScreen(origin);
                AccessibilityNodeInfo first = edgeNode(nodes, dx, dy, origin);
                if (first != null) return focusNode(first);
            }

            AccessibilityNodeInfo best = null;
            double bestScore = Double.MAX_VALUE;
            int cx = origin.centerX();
            int cy = origin.centerY();
            for (AccessibilityNodeInfo node : nodes) {
                if (current != null && node.equals(current)) continue;
                Rect rect = new Rect();
                node.getBoundsInScreen(rect);
                int vx = rect.centerX() - cx;
                int vy = rect.centerY() - cy;
                int primary = dx != 0 ? vx * dx : vy * dy;
                if (primary <= 0) continue;
                int cross = dx != 0 ? Math.abs(vy) : Math.abs(vx);
                double score = primary + cross * 2.0 + (cross * cross) / (double) Math.max(1, primary);
                if (score < bestScore) {
                    bestScore = score;
                    best = node;
                }
            }
            if (best != null) return focusNode(best);
            return scrollInDirection(root, dx, dy);
        } finally {
            for (AccessibilityNodeInfo node : nodes) {
                try { node.recycle(); } catch (Exception ignored) { }
            }
            root.recycle();
        }
    }

    private void collectLeafInteractive(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out) {
        if (node == null || !node.isVisibleToUser()) return;
        boolean interactive = node.isFocusable() || node.isClickable();
        boolean hasInteractiveChild = false;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            if (containsInteractive(child)) hasInteractiveChild = true;
            collectLeafInteractive(child, out);
            child.recycle();
        }
        if (interactive && !hasInteractiveChild) out.add(AccessibilityNodeInfo.obtain(node));
    }

    private boolean containsInteractive(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isVisibleToUser() && (node.isFocusable() || node.isClickable())) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            boolean found = containsInteractive(child);
            if (child != null) child.recycle();
            if (found) return true;
        }
        return false;
    }

    private boolean containsNode(List<AccessibilityNodeInfo> nodes, AccessibilityNodeInfo target) {
        for (AccessibilityNodeInfo node : nodes) if (node.equals(target)) return true;
        return false;
    }

    private AccessibilityNodeInfo nearestTo(List<AccessibilityNodeInfo> nodes, int x, int y) {
        AccessibilityNodeInfo best = null;
        double score = Double.MAX_VALUE;
        for (AccessibilityNodeInfo node : nodes) {
            Rect rect = new Rect();
            node.getBoundsInScreen(rect);
            double d = Math.hypot(rect.centerX() - x, rect.centerY() - y);
            if (d < score) { score = d; best = node; }
        }
        return best;
    }

    private AccessibilityNodeInfo edgeNode(List<AccessibilityNodeInfo> nodes, int dx, int dy, Rect screen) {
        AccessibilityNodeInfo best = null;
        double bestScore = Double.MAX_VALUE;
        for (AccessibilityNodeInfo node : nodes) {
            Rect rect = new Rect();
            node.getBoundsInScreen(rect);
            double score;
            if (dx > 0) score = rect.left + Math.abs(rect.centerY() - screen.centerY()) * 2.0;
            else if (dx < 0) score = -rect.right + Math.abs(rect.centerY() - screen.centerY()) * 2.0;
            else if (dy > 0) score = rect.top + Math.abs(rect.centerX() - screen.centerX()) * 2.0;
            else score = -rect.bottom + Math.abs(rect.centerX() - screen.centerX()) * 2.0;
            if (score < bestScore) { bestScore = score; best = node; }
        }
        return best;
    }

    private boolean focusNode(AccessibilityNodeInfo node) {
        if (node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) return true;
        return node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
    }

    private boolean scrollInDirection(AccessibilityNodeInfo node, int dx, int dy) {
        if (node == null) return false;
        if (node.isScrollable()) {
            int action = (dx > 0 || dy > 0)
                    ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
            if (node.performAction(action)) return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            boolean moved = scrollInDirection(child, dx, dy);
            child.recycle();
            if (moved) return true;
        }
        return false;
    }

    private int getScreenCenterX(AccessibilityNodeInfo root) { Rect r = new Rect(); root.getBoundsInScreen(r); return r.centerX(); }
    private int getScreenCenterY(AccessibilityNodeInfo root) { Rect r = new Rect(); root.getBoundsInScreen(r); return r.centerY(); }

    private boolean clickFocused() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null) focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if (focused != null && focused.isClickable()) return focused.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            List<AccessibilityNodeInfo> nodes = new ArrayList<>();
            collectLeafInteractive(root, nodes);
            AccessibilityNodeInfo nearest = nearestTo(nodes, getScreenCenterX(root), getScreenCenterY(root));
            boolean clicked = nearest != null && nearest.isClickable()
                    && nearest.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            for (AccessibilityNodeInfo node : nodes) node.recycle();
            return clicked;
        } finally {
            root.recycle();
        }
    }
}
