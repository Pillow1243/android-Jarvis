package ai.nexus.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Procedural latitude ribbons, depth-sorted phosphor points and live microphone envelope. No video asset. */
public final class SignalField extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path curve=new Path();private final RectF oval=new RectF();private final float d;private Shader background,halo;private String state="idle";private boolean motion=true;private float rms;private final float[] envelope=new float[28];private final long start=SystemClock.uptimeMillis();
    private static final int LAT=36,LON=88;private static final double[] sin=new double[LON+1],cos=new double[LON+1];static{for(int j=0;j<=LON;j++){sin[j]=Math.sin(j*2*Math.PI/LON);cos[j]=Math.cos(j*2*Math.PI/LON);}}
    public SignalField(Context c){super(c);d=getResources().getDisplayMetrics().density;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    public void update(String s,boolean m){state=s;motion=m;invalidate();}public void level(float r){rms=.7f*rms+.3f*r;System.arraycopy(envelope,1,envelope,0,envelope.length-1);envelope[envelope.length-1]=r;invalidate();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){background=new LinearGradient(0,0,w,h,new int[]{0xff14251d,0xff0c1b15,0xff10231d},null,Shader.TileMode.CLAMP);halo=new RadialGradient(w*.51f,h*.48f,h*.63f,new int[]{0x33499c4e,0x132f713d,0x00000000},null,Shader.TileMode.CLAMP);}
    private void paint(int color,float stroke){p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setColor(color);p.setStrokeWidth(stroke*d);}
    @Override protected void onDraw(Canvas c){int w=getWidth(),h=getHeight();p.setStyle(Paint.Style.FILL);p.setColor(-1);p.setShader(background);c.drawRect(0,0,w,h,p);p.setShader(halo);c.drawRect(0,0,w,h,p);p.setShader(null);
        float cx=w*.51f,cy=h*.47f,R=h*.305f;boolean animate=motion&&ValueAnimator.areAnimatorsEnabled();double time=animate?(SystemClock.uptimeMillis()-start)/1000.:2.;boolean listen=state.equals("listening"),think=state.equals("thinking"),plan=state.equals("planning"),exec=state.equals("executing"),verify=state.equals("verifying"),done=state.equals("completed")||state.equals("handoff"),speaking=state.equals("speaking");double speed=listen?.85:think?1.25:plan?.72:exec?1.7:verify?.95:done?.15:speaking?.9:.32;double t=time*speed;
        int bloom=think?Kit.LILAC:verify?Kit.BLUE:exec?Kit.AMBER:Kit.LIME;R*=1+(listen?.10*rms:done?.025:.014)*Math.sin(t*2.0);
        paint(0x406a987c,.7f);for(float x=10*d;x<w;x+=13*d)for(float y=11*d;y<h;y+=13*d)c.drawPoint(x,y,p);
        paint(0x44739b79,.65f);for(int side:new int[]{-1,1}){float x=cx+side*R*1.6f;for(int j=-2;j<=2;j++)c.drawLine(x,cy+j*5*d,x+(j==0?8:3)*side*d,cy+j*5*d,p);}
        c.save();c.rotate(-20,cx,cy);oval.set(cx-R*1.52f,cy-R*.39f,cx+R*1.52f,cy+R*.39f);paint(0x596b9e79,.7f);c.drawArc(oval,3,196,false,p);paint(0x29538064,.65f);c.drawArc(oval,214,115,false,p);double o=t*.33;c.drawCircle(cx+(float)Math.cos(o)*R*1.52f,cy+(float)Math.sin(o)*R*.39f,2*d,p);c.restore();
        double ct=Math.cos(.46),st=Math.sin(.46),roll=-.23,cr=Math.cos(roll),sr=Math.sin(roll);for(int i=0;i<LAT;i++){double lat=-1.53+i*3.06/(LAT-1),sl=Math.sin(lat),cl=Math.cos(lat);curve.reset();float lastX=0,lastY=0;for(int j=0;j<=LON;j++){double a=j*(2*Math.PI/LON);double ripple=(.113*Math.sin(3*a+2.8*lat+t)+.072*Math.cos(2*a-4*lat-t*.58))*cl;double r=(1+ripple)*cl,xx=r*cos[j],zz=r*sin[j],yy=sl*(1+ripple*.4)+.075*cl*Math.sin(2*a+t+lat*2);double vy=yy*ct-zz*st,z=yy*st+zz*ct;float x=cx+R*(float)(xx*cr-vy*sr),y=cy+R*(float)(vy*cr+xx*sr);if(j==0)curve.moveTo(x,y);else curve.lineTo(x,y);if(j!=0&&z>.02){float light=(float)(.5+.5*Math.sin(a*2.5+lat*3+t*.4));int col=Color.rgb((int)(78+light*132),(int)(177+light*70),(int)(102+light*54));if(think||exec||verify)col=bloom;paint(Kit.shade(col,(int)Math.min(240,52+z*115+light*65)),.65f+(float)z*.34f);c.drawLine(lastX,lastY,x,y,p);if(j%5==0&&i%3==0){p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,(.50f+(float)z*.48f)*d,p);}}lastX=x;lastY=y;}paint(0x3d8acd90,.55f);c.drawPath(curve,p);}
        if(plan||verify||done){paint(Kit.shade(bloom,135),1);oval.set(cx-R*1.24f,cy-R*1.24f,cx+R*1.24f,cy+R*1.24f);c.drawArc(oval,(float)(time*32)%360,done?300:verify?160:65,false,p);}if(exec){paint(Kit.shade(bloom,120),.8f);float y=cy+(float)Math.sin(t)*R;c.drawLine(cx-R,y,cx+R,y,p);}if(listen||speaking){float bx=cx-27*d;for(int i=0;i<envelope.length;i++){float val=listen?envelope[i]:(float)((Math.sin(i*.9+t*5)+1)*.25);paint(Kit.shade(bloom,190),1.2f);c.drawLine(bx+2*i*d,cy+R*1.27f-val*6*d,bx+2*i*d,cy+R*1.27f+val*6*d,p);}}
        if(animate&&getWindowVisibility()==VISIBLE&&isShown())postInvalidateDelayed(listen||speaking||exec?16:50);
    }
}
