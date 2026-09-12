/*
 * Digital Modern — native macOS vibrancy implementation.
 *
 * Compiles to libDigitalVibrancy.dylib and is loaded by the JVM through JNA.
 * See Vibrancy.h for the public C ABI.
 *
 * Threading: AppKit requires NSWindow / NSView / NSVisualEffectView to be
 * touched only on the AppKit main thread. Swing's EDT is *not* the AppKit
 * main thread, so every public entry point in this file dispatches its
 * AppKit work to the main queue via dispatch_sync. dispatch_sync is used
 * (not async) so the JVM-side caller can read back the synchronous result
 * (e.g. the create handle) and so the effect view is fully created before
 * the call returns. The function guards against the deadlock that would
 * happen if the calling thread *is* the main thread.
 */
#import <Cocoa/Cocoa.h>
#import <objc/runtime.h>
#import <pthread.h>
#import <dispatch/dispatch.h>
#import "Vibrancy.h"

/* Run the given block on the AppKit main thread, synchronously. No-op if
 * we are already on the main thread. */
static void DVRunOnMainQueue(dispatch_block_t block) {
    if (pthread_main_np() != 0) {
        block();
        return;
    }
    dispatch_sync(dispatch_get_main_queue(), block);
}

/* Concrete definition, hidden from the public header.
 *
 * The host window is held via __weak so we never reach into a freed NSWindow
 * if the AWT side closes the frame before we get a detach call. The effect
 * view is held strong so AppKit keeps it alive; it is removed from the
 * superview and released on detach. */
struct DVHandleImpl {
    NSView  *effectView;     /* NSVisualEffectView* or NSGlassEffectView* */
    BOOL     isGlass;
    /* Strong ref to the host NSWindow so the effect view's superview
     * remains valid even if the Java side loses its pointer mid-frame.
     * AppKit's NSWindow lifetime is owned by the JVM/AWT; we hold this
     * only as a safety belt for the brief sync calls. */
    NSWindow *hostWindow;
};

/* Cached at first call. macOS 26 ships NSGlassEffectView as a private
 * AppKit class; we probe by name rather than linking the symbol so the
 * dylib still loads on older systems. */
static int g_hasGlassCached = -1;

static Class DVGlassEffectViewClass(void) {
    if (g_hasGlassCached == -1) {
        Class c = NSClassFromString(@"NSGlassEffectView");
        g_hasGlassCached = (c != Nil) ? 1 : 0;
    }
    return g_hasGlassCached ? NSClassFromString(@"NSGlassEffectView") : Nil;
}

/* NSVisualEffectMaterial enum gained new cases over time. Resolve at
 * runtime by integer value so the binary keeps working on older SDKs. */
static BOOL DVSetMaterialByValue(NSVisualEffectView *v, long materialValue) {
    if (![v respondsToSelector:@selector(setMaterial:)]) return NO;
    NSMethodSignature *sig = [v methodSignatureForSelector:@selector(setMaterial:)];
    if (sig == nil) return NO;
    NSInvocation *inv = [NSInvocation invocationWithMethodSignature:sig];
    [inv setTarget:v];
    [inv setSelector:@selector(setMaterial:)];
    [inv setArgument:&materialValue atIndex:2];
    [inv invoke];
    return YES;
}

static NSVisualEffectMaterial DVStyleToMaterial(int style) {
    /* The integer values match NSVisualEffectMaterial:
     *   8=sidebar (10.13+), 9=underWindowBackground, 11=hudWindow.
     * We jump by integer to avoid needing newer SDK enum declarations. */
    switch (style) {
        case 1:  return (NSVisualEffectMaterial)8;   /* sidebar */
        case 2:  return (NSVisualEffectMaterial)9;   /* underWindowBackground */
        case 0:                                  /* hudWindow */
        default: return (NSVisualEffectMaterial)11;
    }
}

/* Apply corner radius to either NSVisualEffectView (via its CALayer) or
 * NSGlassEffectView (via KVC). */
static void DVApplyCornerRadius(NSView *view, BOOL isGlass, double r) {
    if (isGlass) {
        [view setValue:@(r) forKey:@"cornerRadius"];
        return;
    }
    [view setWantsLayer:YES];
    CALayer *layer = [view layer];
    if (layer != nil) {
        [layer setCornerRadius:(CGFloat)r];
        [layer setMasksToBounds:(r > 0.0)];
    }
}

/* Convert a top-left-origin y inside the content view to AppKit's
 * bottom-left coordinate system when the content view isn't already
 * flipped. AWT's CPlatformView is not flipped, so this conversion matters
 * for the common case; some embedders (or future AWT versions) may flip
 * the content view themselves. */
static CGFloat DVAdjustYForContentView(NSView *contentView, CGFloat y, CGFloat h) {
    if ([contentView isFlipped]) return y;
    CGFloat hContent = [contentView bounds].size.height;
    return hContent - y - h;
}

/* ---------- public C ABI ----------------------------------------------- */

int dv_vibrancy_has_glass(void) {
    /* Pure runtime class probe; no AppKit state. Safe from any thread. */
    (void)DVGlassEffectViewClass();
    return g_hasGlassCached;
}

DVHandle dv_vibrancy_attach(void *hostWindowHandle, int style, double cornerRadius) {
    if (hostWindowHandle == NULL) return NULL;
    NSWindow *host = (__bridge NSWindow *)hostWindowHandle;
    __block DVHandle out = NULL;
    DVRunOnMainQueue(^{
        @autoreleasepool {
            NSView *content = [host contentView];
            if (content == nil) return;

            struct DVHandleImpl *h = (struct DVHandleImpl *)calloc(1, sizeof(*h));
            if (h == NULL) return;
            h->hostWindow = host;

            Class glassCls = DVGlassEffectViewClass();
            NSView *effectView = nil;
            if (glassCls != Nil) {
                /* macOS 26+: NSGlassEffectView IS the Liquid Glass look. The
                 * class is private, so we use KVC for cornerRadius. */
                effectView = [[glassCls alloc] initWithFrame:content.bounds];
                if (effectView == nil) { free(h); return; }
                h->isGlass = YES;
            } else {
                effectView = [[NSVisualEffectView alloc] initWithFrame:content.bounds];
                if (effectView == nil) { free(h); return; }
                DVSetMaterialByValue((NSVisualEffectView *)effectView,
                                     (long)DVStyleToMaterial(style));
                [(NSVisualEffectView *)effectView setState:NSVisualEffectStateFollowsWindowActiveState];
                [(NSVisualEffectView *)effectView setBlendingMode:NSVisualEffectBlendingModeBehindWindow];
                h->isGlass = NO;
            }
            h->effectView = effectView;
            [effectView setAutoresizingMask:NSViewWidthSizable | NSViewHeightSizable];
            DVApplyCornerRadius(effectView, h->isGlass, cornerRadius);

            /* A child of the AWT view composites ABOVE Java2D, even when
             * positioned NSWindowBelow. Use a sibling behind the entire
             * AWT view so sidebar text and controls remain sharp. */
            NSView *container = [content superview];
            if (container == nil) { free(h); return; }
            [effectView setAutoresizingMask:NSViewNotSizable];
            [effectView setHidden:YES];
            [container addSubview:effectView
                       positioned:NSWindowBelow
                       relativeTo:content];

            out = (DVHandle)h;
        }
    });
    return out;
}

void dv_vibrancy_set_frame(DVHandle handle, double x, double y, double w, double h) {
    if (handle == NULL) return;
    struct DVHandleImpl *impl = (struct DVHandleImpl *)handle;
    DVRunOnMainQueue(^{
        @autoreleasepool {
            NSView *view = impl->effectView;
            if (view == nil || [view superview] == nil) return;
            NSView *content = [impl->hostWindow contentView];
            if (content == nil) return;
            CGFloat xa = (CGFloat)x;
            CGFloat wa = (CGFloat)w;
            CGFloat ha = (CGFloat)h;
            CGFloat ya = DVAdjustYForContentView(content, (CGFloat)y, ha);
            NSRect localFrame = NSMakeRect(xa, ya, wa, ha);
            [view setFrame:[content convertRect:localFrame toView:[view superview]]];
        }
    });
}

void dv_vibrancy_set_hidden(DVHandle handle, int hidden) {
    if (handle == NULL) return;
    struct DVHandleImpl *impl = (struct DVHandleImpl *)handle;
    DVRunOnMainQueue(^{
        @autoreleasepool {
            NSView *view = impl->effectView;
            if (view == nil) return;
            [view setHidden:(hidden != 0)];
        }
    });
}

void dv_vibrancy_set_window_transparent(void *windowHandle, int transparent) {
    if (windowHandle == NULL) return;
    NSWindow *window = (__bridge NSWindow *)windowHandle;
    DVRunOnMainQueue(^{
        @autoreleasepool {
            [window setOpaque:(transparent == 0)];
            if (transparent != 0) {
                [window setBackgroundColor:[NSColor clearColor]];
                /* AWT may keep an opaque layer on the content view even after
                 * the NSWindow itself is marked transparent. Clear both
                 * levels so the desktop/window beneath the Java window can
                 * actually be composited through the sidebar. */
                NSView *content = [window contentView];
                [content setWantsLayer:YES];
                [content setNeedsDisplay:YES];
                CALayer *layer = [content layer];
                if (layer != nil) {
                    [layer setOpaque:NO];
                    [layer setBackgroundColor:[NSColor clearColor].CGColor];
                }
            }
        }
    });
}

void dv_vibrancy_set_corner_radius(DVHandle handle, double r) {
    if (handle == NULL) return;
    struct DVHandleImpl *impl = (struct DVHandleImpl *)handle;
    DVRunOnMainQueue(^{
        @autoreleasepool {
            NSView *view = impl->effectView;
            if (view == nil) return;
            DVApplyCornerRadius(view, impl->isGlass, r);
        }
    });
}

void dv_vibrancy_set_active(DVHandle handle, int active) {
    if (handle == NULL) return;
    struct DVHandleImpl *impl = (struct DVHandleImpl *)handle;
    DVRunOnMainQueue(^{
        @autoreleasepool {
            if (impl->isGlass) return; /* NSGlassEffectView tracks state on its own */
            NSView *view = impl->effectView;
            if (view == nil || ![view isKindOfClass:[NSVisualEffectView class]]) return;
            if (active < 0) {
                [(NSVisualEffectView *)view setState:NSVisualEffectStateFollowsWindowActiveState];
            } else {
                NSVisualEffectState st = active
                    ? NSVisualEffectStateActive
                    : NSVisualEffectStateInactive;
                [(NSVisualEffectView *)view setState:st];
            }
        }
    });
}

void dv_vibrancy_detach(DVHandle handle) {
    if (handle == NULL) return;
    struct DVHandleImpl *impl = (struct DVHandleImpl *)handle;
    DVRunOnMainQueue(^{
        @autoreleasepool {
            NSView *view = impl->effectView;
            if (view != nil) {
                [view setHidden:YES];
                [view removeFromSuperview];
            }
            impl->effectView = nil;
            impl->hostWindow = nil;
        }
    });
    /* impl was calloc'd and is not referenced after detach; freeing on the
     * calling thread keeps JNA from observing a use-after-free if AppKit
     * tears the view down on the main thread slightly later. */
    free(impl);
}
