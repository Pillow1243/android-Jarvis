package ai.nexus.ui;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.text.*;
import android.view.*;
import android.widget.*;

/** Small native design system: Nightglass surfaces, phosphor actions and instrument labels. */
public final class Kit {

  public final Activity a;
  public boolean rtl;
  public int bg = 0xff0c100f,
    card = 0xff151b19,
    raised = 0xff1d2521,
    line = 0xff2b342e,
    ink = 0xfff0f4ed,
    sub = 0xffa4afa6,
    muted = 0xff8b9b91;
  public static final int LIME = 0xffbcf985,
    TEAL = 0xff83dbce,
    BLUE = 0xff93c4ff,
    AMBER = 0xffffbd8f,
    LILAC = 0xffc2afff,
    RED = 0xffff9e91;

  public Kit(Activity a) {
    this.a = a;
  }

  public void theme(boolean blue) {
    bg = blue ? 0xff0c0e16 : 0xff0c100f;
    card = blue ? 0xff171a27 : 0xff151b19;
    raised = blue ? 0xff222634 : 0xff202822;
    line = blue ? 0xff303548 : 0xff2c362f;
  }

  public int d(float n) {
    return Math.round(n * a.getResources().getDisplayMetrics().density);
  }

  public static int shade(int c, int alpha) {
    return (c & 0xffffff) | (alpha << 24);
  }

  public GradientDrawable shape(int fill, float r, int border) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(fill);
    g.setCornerRadius(d(r));
    if (border != 0) g.setStroke(d(1), border);
    return g;
  }

  public void box(View v, int fill, int r, int stroke) {
    v.setBackground(shape(fill, r, stroke));
  }

  public void tap(View v, Runnable run, String desc) {
    Drawable b = v.getBackground();
    v.setBackground(new RippleDrawable(ColorStateList.valueOf(shade(LIME, 42)), b, shape(-1, 14, 0)));
    v.setClickable(true);
    v.setFocusable(true);
    if (desc != null) v.setContentDescription(desc);
    v.setOnClickListener(x -> {
      x.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
      run.run();
    });
  }

  public void pad(View v, int l, int t, int r, int b) {
    v.setPaddingRelative(d(l), d(t), d(r), d(b));
  }

  public LinearLayout col() {
    LinearLayout v = new LinearLayout(a);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  public LinearLayout row() {
    LinearLayout v = new LinearLayout(a);
    v.setOrientation(LinearLayout.HORIZONTAL);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  public void add(LinearLayout v, View child, int w, int h) {
    v.addView(child, new LinearLayout.LayoutParams(w < 0 ? w : d(w), h < 0 ? h : d(h)));
  }

  public void weight(LinearLayout v, View child, int h) {
    v.addView(child, new LinearLayout.LayoutParams(0, h < 0 ? h : d(h), 1));
  }

  public void gap(LinearLayout v, int size) {
    View g = new View(a);
    if (v.getOrientation() == LinearLayout.VERTICAL) add(v, g, 1, size);
    else add(v, g, size, 1);
  }

  public TextView text(String s, float size, int color, int style) {
    TextView v = new TextView(a);
    v.setText(s);
    v.setTextColor(color);
    v.setTextSize(size);
    v.setIncludeFontPadding(false);
    v.setTypeface(
      Typeface.create(
        style == 2 ? "monospace" : style == 1 ? "sans-serif-medium" : "sans-serif",
        Typeface.NORMAL
      )
    );
    if (style == 2 && !rtl) v.setLetterSpacing(.07f);
    v.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
    return v;
  }

  public void single(TextView t) {
    t.setMaxLines(1);
    t.setEllipsize(TextUtils.TruncateAt.END);
  }

  public ImageView icon(String name, int color) {
    ImageView i = new ImageView(a);
    int id = a.getResources().getIdentifier("ic_" + name, "drawable", a.getPackageName());
    i.setImageResource(id);
    i.setColorFilter(color);
    i.setScaleType(ImageView.ScaleType.FIT_CENTER);
    i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    return i;
  }

  public FrameLayout glyph(String name, int color, int boxSize, int glyph) {
    FrameLayout f = new FrameLayout(a);
    box(f, shade(color, 22), Math.max(9, boxSize / 3), 0);
    f.addView(icon(name, color), new FrameLayout.LayoutParams(d(glyph), d(glyph), Gravity.CENTER));
    return f;
  }

  public TextView badge(String s, int color) {
    TextView v = text(s, 10, color, 2);
    box(v, shade(color, 20), 20, shade(color, 52));
    pad(v, 9, 5, 9, 5);
    v.setGravity(Gravity.CENTER);
    return v;
  }

  public LinearLayout card() {
    LinearLayout c = col();
    box(c, card, 17, line);
    pad(c, 16, 15, 16, 15);
    return c;
  }

  public TextView button(String s, boolean primary, Runnable cb) {
    TextView b = text(s, 15, primary ? 0xff17230f : ink, 1);
    b.setGravity(Gravity.CENTER);
    b.setMinHeight(d(48));
    pad(b, 12, 10, 12, 10);
    box(b, primary ? LIME : raised, 14, primary ? 0 : line);
    tap(b, cb, s);
    return b;
  }

  public EditText input(String hint, String value, boolean multi) {
    EditText e = new EditText(a);
    box(e, bg, 13, line);
    pad(e, 14, 12, 14, 12);
    e.setTextColor(ink);
    e.setHintTextColor(muted);
    e.setTextSize(16);
    e.setSingleLine(!multi);
    e.setMinHeight(d(multi ? 82 : 50));
    if (multi) {
      e.setMaxLines(6);
      e.setGravity(Gravity.TOP | Gravity.START);
    }
    e.setText(value);
    e.setHint(hint);
    return e;
  }

  public void line(LinearLayout v) {
    View l = new View(a);
    l.setBackgroundColor(line);
    v.addView(l, new LinearLayout.LayoutParams(-1, 1));
  }

  public void section(LinearLayout v, String title, String meta) {
    gap(v, 20);
    LinearLayout r = row();
    weight(r, text(title, 18, ink, 1), -2);
    add(r, text(meta, 10, muted, 2), -2, -2);
    add(v, r, -1, -2);
    gap(v, 10);
  }

  public void item(
    LinearLayout parent,
    String icon,
    int tint,
    String name,
    String detail,
    Runnable click
  ) {
    LinearLayout r = row();
    box(r, card, 14, line);
    pad(r, 13, 12, 12, 12);
    add(r, glyph(icon, tint, 36, 19), 36, 36);
    gap(r, 12);
    LinearLayout words = col();
    add(words, text(name, 15, ink, 1), -1, -2);
    if (detail != null && !detail.isEmpty()) {
      gap(words, 4);
      TextView b = text(detail, 12, sub, 0);
      b.setMaxLines(3);
      b.setEllipsize(TextUtils.TruncateAt.END);
      add(words, b, -1, -2);
    }
    weight(r, words, -2);
    if (click != null) {
      gap(r, 5);
      add(r, icon("chevron_right", muted), 16, 16);
      tap(r, click, name);
    }
    add(parent, r, -1, -2);
    gap(parent, 7);
  }

  public class Sheet {

    public final Dialog dialog;
    public final LinearLayout body;
    public final LinearLayout root;
    private final ScrollView sc;

    public Sheet(String title) {
      dialog = new Dialog(a);
      dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
      root = col();
      root.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
      box(root, card, 24, line);
      pad(root, 20, 12, 20, 18);
      View handle = new View(a);
      box(handle, line, 2, 0);
      LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(d(32), d(4));
      hp.gravity = Gravity.CENTER_HORIZONTAL;
      root.addView(handle, hp);
      gap(root, 17);
      add(root, text("NEXUS   /   CONTROL", 10, LIME, 2), -1, -2);
      gap(root, 7);
      add(root, text(title, 24, ink, 1), -1, -2);
      gap(root, 16);
      body = col();
      sc = new ScrollView(a) {
        @Override
        protected void onMeasure(int w, int h) {
          int cap = (int) (a.getResources().getDisplayMetrics().heightPixels * .66);
          super.onMeasure(
            w,
            View.MeasureSpec.makeMeasureSpec(
              Math.min(cap, View.MeasureSpec.getSize(h) > 0 ? View.MeasureSpec.getSize(h) : cap),
              View.MeasureSpec.AT_MOST
            )
          );
        }
      };
      sc.setFillViewport(false);
      sc.addView(body);
      add(root, sc, -1, -2);
      dialog.setContentView(root);
      Window w = dialog.getWindow();
      w.setBackgroundDrawableResource(android.R.color.transparent);
      w.setGravity(Gravity.BOTTOM);
      w.setLayout(-1, -2);
      w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
      w.setNavigationBarColor(card);
    }

    public void note(String text) {
      TextView v = Kit.this.text(text, 14, sub, 0);
      v.setLineSpacing(d(2), 1f);
      add(body, v, -1, -2);
      gap(body, 12);
    }

    public void action(String title, boolean primary, Runnable cb) {
      add(body, button(title, primary, cb), -1, -2);
      gap(body, 6);
    }

    public void show() {
      dialog.show();
      dialog
        .getWindow()
        .setLayout(Math.min(a.getResources().getDisplayMetrics().widthPixels, d(560)), -2);
    }

    public void close() {
      dialog.dismiss();
    }
  }
}
