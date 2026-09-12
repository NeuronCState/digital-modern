/*
 * Digital Modern — native macOS vibrancy bridge.
 *
 * Hosts an NSVisualEffectView (macOS 13+) or NSGlassEffectView (macOS 26+,
 * "Liquid Glass") as a real child of the AWT NSWindow's content view, so
 * the effect composites through Swing's transparent pixels instead of
 * fighting a floating panel on top.
 *
 * Style enum:
 *   0  HUD window material (always available, ChatGPT-sidebar look on
 *      macOS 13/14/15)
 *   1  Sidebar material (macOS 14+)
 *   2  Under-window background (classic vibrancy)
 *
 * macOS 26 ignores the style argument and uses NSGlassEffectView directly.
 *
 * All coordinates passed in are in the host content view's local space with
 * top-left origin, matching Java's Component.getBounds(). The bridge flips
 * the y axis internally if the content view isn't already flipped.
 *
 * Threading: AppKit requires NSWindow / NSView work to happen on the AppKit
 * main thread. Every public entry point dispatches its AppKit work to the
 * main queue via dispatch_sync (with a same-thread short-circuit).
 */
#ifndef DIGITAL_VIBRANCY_H
#define DIGITAL_VIBRANCY_H

#ifdef __cplusplus
extern "C" {
#endif

/* Opaque handle. Do not dereference from the Java side. */
typedef struct DVHandleImpl *DVHandle;

/* Returns 1 if NSGlassEffectView (macOS 26 "Liquid Glass") is available, 0
 * otherwise. Safe to call before dv_vibrancy_attach(). */
int   dv_vibrancy_has_glass(void);

/* Inserts a vibrancy effect view as the bottom-most child of the host
 * NSWindow's content view. The view is initially 0x0 at (0,0); use
 * dv_vibrancy_set_frame to position and size it. The effect view
 * automatically resizes with the content view (autoresizingMask =
 * NSViewWidthSizable | NSViewHeightSizable), but dv_vibrancy_set_frame is
 * still required to define which sub-rectangle it covers.
 *
 * hostWindow is the AWT NSWindow pointer obtained from JNA's
 * Native.getWindowPointer(). Returns NULL on failure. The returned handle
 * must be released with dv_vibrancy_detach(). */
DVHandle dv_vibrancy_attach(void *hostWindow, int style, double cornerRadius);

/* Position and size the effect view inside the host content view, in the
 * content view's local coordinates with top-left origin. x/y/w/h are in
 * points. The effect view is moved/resized without being re-added. */
void  dv_vibrancy_set_frame(DVHandle handle, double x, double y, double w, double h);

/* Show (hidden=0) or hide (hidden=1) the effect view. Hiding does not
 * detach; the view stays in the hierarchy so a later show is cheap. */
void  dv_vibrancy_set_hidden(DVHandle handle, int hidden);

/* Update the corner radius (in points) at runtime. */
void  dv_vibrancy_set_corner_radius(DVHandle handle, double r);

/* Forces active/inactive state. Pass -1 to follow the host window
 * automatically. The Java side wires this to the Swing window's
 * activated/deactivated events. */
void  dv_vibrancy_set_active(DVHandle handle, int active);

/* Make the AWT NSWindow itself transparent so the effect layer and the
 * real desktop behind it can be composited through Swing's alpha rows.
 * Pass 0 to restore the default opaque window. */
void  dv_vibrancy_set_window_transparent(void *windowHandle, int transparent);

/* Removes the effect view from the host content view and releases the
 * handle. Safe to call with NULL. After this returns, no further calls
 * with this handle are valid. */
void  dv_vibrancy_detach(DVHandle handle);

#ifdef __cplusplus
}
#endif

#endif /* DIGITAL_VIBRANCY_H */
