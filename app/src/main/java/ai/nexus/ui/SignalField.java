package ai.nexus.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Procedural latitude ribbons, depth-sorted phosphor points and live microphone envelope. No video asset. */
public final class SignalField extends View {

  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF oval = new RectF();
  private final float d;
  private Shader background, halo;
  private String state = "idle";
  private boolean motion = true;
  private float rms;
  private final float[] envelope = new float[28];
  private final long start = SystemClock.uptimeMillis();
  private static final int LAT = 38,
    LON = 80,
    BANDS = 10;
  private final Path[] bright = new Path[BANDS];
  private float[] grid = new float[0];
  private final float[] nodes = new float[LAT * LON * 2];
  private final Path back = new Path();
  private static final double[] cos2 = new double[LON + 1],
    sin2 = new double[LON + 1],
    cos3 = new double[LON + 1],
    sin3 = new double[LON + 1],
    cosLight = new double[LON + 1],
    sinLight = new double[LON + 1];
  private static final double[] sin = new double[LON + 1],
    cos = new double[LON + 1];

  static {
    for (int j = 0; j <= LON; j++) {
      sin[j] = Math.sin((j * 2 * Math.PI) / LON);
      double a = (j * 2 * Math.PI) / LON;
      cos[j] = Math.cos(a);
      cos2[j] = Math.cos(2 * a);
      sin2[j] = Math.sin(2 * a);
      cos3[j] = Math.cos(3 * a);
      sin3[j] = Math.sin(3 * a);
      cosLight[j] = Math.cos(2.5 * a);
      sinLight[j] = Math.sin(2.5 * a);
    }
  }

  public SignalField(Context c) {
    super(c);
    d = getResources().getDisplayMetrics().density;
    for (int i = 0; i < BANDS; i++) bright[i] = new Path();
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  public void update(String s, boolean m) {
    state = s;
    motion = m;
    invalidate();
  }

  public void level(float r) {
    rms = .7f * rms + .3f * r;
    System.arraycopy(envelope, 1, envelope, 0, envelope.length - 1);
    envelope[envelope.length - 1] = r;
    invalidate();
  }

  @Override
  protected void onSizeChanged(int w, int h, int ow, int oh) {
    int columns = (int) (w / (13 * d)) + 1,
      rows = (int) (h / (13 * d)) + 1;
    grid = new float[columns * rows * 2];
    int gi = 0;
    for (int x = 0; x < columns; x++) for (int y = 0; y < rows; y++) {
      grid[gi++] = (10 + x * 13) * d;
      grid[gi++] = (11 + y * 13) * d;
    }
    background = new LinearGradient(
      0,
      0,
      w,
      h,
      new int[] { 0xff14251d, 0xff0c1b15, 0xff10231d },
      null,
      Shader.TileMode.CLAMP
    );
    halo = new RadialGradient(
      w * .51f,
      h * .48f,
      h * .63f,
      new int[] { 0x33499c4e, 0x132f713d, 0x00000000 },
      null,
      Shader.TileMode.CLAMP
    );
  }

  private void paint(int color, float stroke) {
    p.setShader(null);
    p.setStyle(Paint.Style.STROKE);
    p.setColor(color);
    p.setStrokeWidth(stroke * d);
  }

  @Override
  protected void onDraw(Canvas c) {
    int w = getWidth(),
      h = getHeight();
    p.setStyle(Paint.Style.FILL);
    p.setColor(-1);
    p.setShader(background);
    c.drawRect(0, 0, w, h, p);
    p.setShader(halo);
    c.drawRect(0, 0, w, h, p);
    p.setShader(null);
    float cx = w * .51f,
      cy = h * .47f,
      R = h * .305f;
    boolean animate = motion && ValueAnimator.areAnimatorsEnabled();
    double time = animate ? (SystemClock.uptimeMillis() - start) / 1000. : 2.;
    boolean listen = state.equals("listening"),
      think = state.equals("thinking"),
      plan = state.equals("planning"),
      exec = state.equals("executing"),
      verify = state.equals("verifying"),
      done = state.equals("completed") || state.equals("handoff"),
      speaking = state.equals("speaking");
    double speed = listen
      ? .85
      : think
        ? 1.25
        : plan
          ? .72
          : exec
            ? 1.7
            : verify
              ? .95
              : done
                ? .15
                : speaking
                  ? .9
                  : .32;
    double t = time * speed;
    int bloom = think ? Kit.LILAC : verify ? Kit.BLUE : exec ? Kit.AMBER : Kit.LIME;
    R *= 1 + (listen ? .10 * rms : done ? .025 : .014) * Math.sin(t * 2.0);
    paint(0x406a987c, .7f);
    c.drawPoints(grid, p);
    paint(0x44739b79, .65f);
    for (int side = -1; side <= 1; side += 2) {
      float x = cx + side * R * 1.6f;
      for (int j = -2; j <= 2; j++) c.drawLine(
        x,
        cy + j * 5 * d,
        x + (j == 0 ? 8 : 3) * side * d,
        cy + j * 5 * d,
        p
      );
    }
    c.save();
    c.rotate(-20, cx, cy);
    oval.set(cx - R * 1.52f, cy - R * .39f, cx + R * 1.52f, cy + R * .39f);
    paint(0x596b9e79, .7f);
    c.drawArc(oval, 3, 196, false, p);
    paint(0x29538064, .65f);
    c.drawArc(oval, 214, 115, false, p);
    double o = t * .33;
    c.drawCircle(cx + (float) Math.cos(o) * R * 1.52f, cy + (float) Math.sin(o) * R * .39f, 2 * d, p);
    c.restore();
    // Batch the mesh into eleven paths rather than issuing a separate GPU command for every segment.
    for (Path path : bright) path.rewind();
    back.rewind();
    int count = 0;
    double ct = .89605,
      st = .44395,
      cr = .97367,
      sr = -.22798;
    for (int i = 0; i < LAT; i++) {
      double lat = -1.53 + (i * 3.06) / (LAT - 1),
        sl = Math.sin(lat),
        cl = Math.cos(lat);
      double c1 = Math.cos(2.8 * lat + t),
        s1 = Math.sin(2.8 * lat + t),
        c2 = Math.cos(-4 * lat - t * .58),
        s2 = Math.sin(-4 * lat - t * .58),
        c3 = Math.cos(t + lat * 2),
        s3 = Math.sin(t + lat * 2),
        c4 = Math.cos(lat * 3 + t * .4),
        s4 = Math.sin(lat * 3 + t * .4);
      float lastX = 0,
        lastY = 0;
      int prevBand = -1;
      for (int j = 0; j <= LON; j++) {
        double ripple =
          (.125 * (sin3[j] * c1 + cos3[j] * s1) + .085 * (cos2[j] * c2 - sin2[j] * s2)) * cl;
        double r = (1 + ripple) * cl,
          xx = r * cos[j],
          zz = r * sin[j],
          yy = sl * (1 + ripple * .4) + .085 * cl * (sin2[j] * c3 + cos2[j] * s3);
        double vy = yy * ct - zz * st,
          z = yy * st + zz * ct;
        float x = cx + R * (float) (xx * cr - vy * sr),
          y = cy + R * (float) (vy * cr + xx * sr);
        if (j == 0) back.moveTo(x, y);
        else back.lineTo(x, y);
        if (j != 0 && z > .02) {
          double light = .5 + .5 * (sinLight[j] * c4 + cosLight[j] * s4);
          int band = Math.max(0, Math.min(BANDS - 1, (int) ((.56 * z + .44 * light) * BANDS)));
          if (band != prevBand) bright[band].moveTo(lastX, lastY);
          bright[band].lineTo(x, y);
          prevBand = band;
          if (j % 5 == 0 && i % 3 == 0) {
            nodes[count++] = x;
            nodes[count++] = y;
          }
        } else prevBand = -1;
        lastX = x;
        lastY = y;
      }
    }
    paint(0x4281bd91, .55f);
    c.drawPath(back, p);
    for (int b = 0; b < BANDS; b++) {
      float v = b / (BANDS - 1f);
      int col = think || exec || verify
        ? bloom
        : Color.rgb((int) (80 + 164 * v), (int) (185 + 69 * v), (int) (123 + 61 * v));
      paint(Kit.shade(col, (int) (99 + 153 * v)), .65f + .48f * v);
      c.drawPath(bright[b], p);
    }
    paint(0xdfd7ffc3, 1.6f);
    p.setStrokeCap(Paint.Cap.ROUND);
    c.drawPoints(nodes, 0, count, p);
    p.setStrokeCap(Paint.Cap.BUTT);
    if (plan || verify || done) {
      paint(Kit.shade(bloom, 135), 1);
      oval.set(cx - R * 1.24f, cy - R * 1.24f, cx + R * 1.24f, cy + R * 1.24f);
      c.drawArc(oval, (float) (time * 32) % 360, done ? 300 : verify ? 160 : 65, false, p);
    }
    if (exec) {
      paint(Kit.shade(bloom, 120), .8f);
      float y = cy + (float) Math.sin(t) * R;
      c.drawLine(cx - R, y, cx + R, y, p);
    }
    if (listen || speaking) {
      float bx = cx - 27 * d;
      for (int i = 0; i < envelope.length; i++) {
        float val = listen ? envelope[i] : (float) ((Math.sin(i * .9 + t * 5) + 1) * .25);
        paint(Kit.shade(bloom, 190), 1.2f);
        c.drawLine(
          bx + 2 * i * d,
          cy + R * 1.27f - val * 6 * d,
          bx + 2 * i * d,
          cy + R * 1.27f + val * 6 * d,
          p
        );
      }
    }
    if (animate && getWindowVisibility() == VISIBLE && isShown()) postInvalidateDelayed(
      listen || speaking || exec ? 16 : 50
    );
  }
}
