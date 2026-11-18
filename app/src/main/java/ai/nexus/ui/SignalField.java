package ai.nexus.ui;

import ai.nexus.R;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.view.View;

/** One cached mesh texture plus live overlays. Standby settles; it never runs an always-on frame loop. */
public final class SignalField extends View {

  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final RectF bounds = new RectF(),
    visibleBounds = new RectF();
  private final Rect visible = new Rect();
  private final Bitmap mesh;
  private final float d;
  private final float[] envelope = new float[28];
  private final ColorFilter planning = new LightingColorFilter(0xffa7a5ff, 0x002b1642),
    verifying = new LightingColorFilter(0xffade5ff, 0x00071e39),
    executing = new LightingColorFilter(0xffffb684, 0x00281903);
  private float[] grid = new float[0];
  private Shader bg;
  private String state = "idle";
  private boolean motion = true,
    windowActive = true;
  private float rms;
  private long entered = SystemClock.uptimeMillis();

  public SignalField(Context c) {
    super(c);
    d = getResources().getDisplayMetrics().density;
    mesh = ((BitmapDrawable) c.getDrawable(R.drawable.nexus_core)).getBitmap();
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  public void update(String s, boolean m) {
    if (!state.equals(s)) entered = SystemClock.uptimeMillis();
    state = s;
    motion = m;
    invalidate();
  }

  public void level(float r) {
    rms = .7f * rms + .3f * r;
    System.arraycopy(envelope, 1, envelope, 0, envelope.length - 1);
    envelope[envelope.length - 1] = r;
    postInvalidateOnAnimation();
  }

  @Override
  protected void onWindowVisibilityChanged(int v) {
    super.onWindowVisibilityChanged(v);
    windowActive = v == VISIBLE;
    if (windowActive) invalidate();
  }

  @Override
  protected void onSizeChanged(int w, int h, int ow, int oh) {
    int cols = (int) (w / (14 * d)) + 1,
      rows = (int) (h / (14 * d)) + 1;
    grid = new float[2 * cols * rows];
    int n = 0;
    for (int x = 0; x < cols; x++) for (int y = 0; y < rows; y++) {
      grid[n++] = (8 + x * 14) * d;
      grid[n++] = (9 + y * 14) * d;
    }
    bg = new LinearGradient(
      0,
      0,
      w,
      h,
      new int[] { 0xff14271e, 0xff0e1f19, 0xff121d20 },
      null,
      Shader.TileMode.CLAMP
    );
  }

  private void line(int col, float stroke) {
    p.setColorFilter(null);
    p.setShader(null);
    p.setColor(col);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(stroke * d);
  }

  @Override
  protected void onDraw(Canvas c) {
    float w = getWidth(),
      h = getHeight(),
      x = w * .51f,
      y = h * .47f,
      radius = h * .32f;
    boolean listen = state.equals("listening"),
      think = state.equals("thinking") || state.equals("planning"),
      exec = state.equals("executing"),
      verify = state.equals("verifying") || state.equals("transcribing"),
      speak = state.equals("speaking"),
      done = state.equals("completed") || state.equals("handoff");
    float elapsed = (SystemClock.uptimeMillis() - entered) / 1000f;
    boolean animated = motion && ValueAnimator.areAnimatorsEnabled();
    boolean active = listen || think || exec || verify || speak;
    float t = animated ? (active ? elapsed : Math.min(1, elapsed)) : 1;
    int accent = think ? Kit.LILAC : exec ? Kit.AMBER : verify ? Kit.BLUE : Kit.LIME;
    p.setColorFilter(null);
    p.setColor(-1);
    p.setStyle(Paint.Style.FILL);
    p.setShader(bg);
    c.drawRect(0, 0, w, h, p);
    line(0x486a987c, .7f);
    c.drawPoints(grid, p);
    for (int side = -1; side <= 1; side += 2) {
      float tx = x + side * radius * 1.59f;
      for (int j = -2; j <= 2; j++) c.drawLine(
        tx,
        y + j * 5 * d,
        tx + side * (j == 0 ? 7 : 3) * d,
        y + j * 5 * d,
        p
      );
    }
    c.save();
    c.rotate(-20, x, y);
    bounds.set(x - radius * 1.48f, y - radius * .36f, x + radius * 1.48f, y + radius * .36f);
    line(0x657ba17e, .65f);
    c.drawArc(bounds, 3, 190, false, p);
    line(0x3a72816c, .65f);
    c.drawArc(bounds, 213, 125, false, p);
    double phase = .6 + t * (exec ? .95 : think ? .62 : .32);
    line(Kit.shade(accent, 135), 1);
    c.drawCircle(
      x + (float) Math.cos(phase) * radius * 1.48f,
      y + (float) Math.sin(phase) * radius * .36f,
      1.9f * d,
      p
    );
    c.restore();
    float swell =
      1 +
      (listen ? .16f * rms : active ? .026f * (float) Math.sin(t * (exec ? 4 : 2)) : .025f * (t - 1));
    float span = radius * 1.30f * swell;
    c.save();
    c.rotate(active ? (float) Math.sin(t * .68) * 6 : 2 * (1 - t), x, y);
    visibleBounds.set(x - span, y - span, x + span, y + span);
    p.setStyle(Paint.Style.FILL);
    p.setColor(-1);
    p.setColorFilter(think ? planning : verify ? verifying : exec ? executing : null);
    c.drawBitmap(mesh, null, visibleBounds, p);
    c.restore();
    if (think || verify || done) {
      line(Kit.shade(accent, 140), 1);
      bounds.set(x - radius * 1.24f, y - radius * 1.24f, x + radius * 1.24f, y + radius * 1.24f);
      c.drawArc(bounds, (t * 32) % 360, done ? 296 : verify ? 145 : 68, false, p);
    }
    if (exec) {
      line(Kit.shade(accent, 160), 1);
      float scan = y + radius * (float) Math.sin(t * 2);
      c.drawLine(x - radius, scan, x + radius, scan, p);
    }
    if (listen || speak) {
      line(Kit.shade(accent, 205), 1.2f);
      float cy = y + radius * 1.27f;
      for (int i = 0; i < envelope.length; i++) {
        float gain = listen ? envelope[i] : (float) ((Math.sin(i * .8 + t * 5) + 1) * .25);
        float bx = x + (i - 13.5f) * 2 * d;
        c.drawLine(bx, cy - gain * 8 * d, bx, cy + gain * 8 * d, p);
      }
    }
    p.setColorFilter(null);
    if (
      animated && (active || elapsed < 1) && windowActive && isShown() && getLocalVisibleRect(visible)
    ) postInvalidateDelayed(active ? 32 : 50);
  }
}
