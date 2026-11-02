package com.snowglobe.overlay;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.util.Random;
public class SnowView extends View {
 private final Paint p=new Paint(3);private final Random rng=new Random();
 private static final int N=95;
 private final float[] x=new float[N],y=new float[N],speed=new float[N],size=new float[N],angle=new float[N],spin=new float[N],drift=new float[N];
 private long last=System.nanoTime();private float time=0;private boolean running=true;
 public SnowView(Context c){super(c);for(int i=0;i<N;i++){x[i]=rng.nextFloat();y[i]=rng.nextFloat();speed[i]=.04f+rng.nextFloat()*.12f;size[i]=2+rng.nextFloat()*5;angle[i]=rng.nextFloat()*360;spin[i]=(rng.nextFloat()-.5f)*100;drift[i]=rng.nextFloat()*6.28f;}}
 public void setAnimating(boolean b){running=b;last=System.nanoTime();if(b)postInvalidateOnAnimation();}
 public void burst(){for(int i=0;i<18;i++)y[i]=rng.nextFloat()*.3f;invalidate();}
 private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float width){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setColor(color);p.setAlpha(255);c.drawLine(x1,y1,x2,y2,p);p.setStyle(Paint.Style.FILL);}
 private void lights(Canvas c,int w,int h,int mode){
  int count=22;int[] colors={0xffff6d77,0xff7be6b4,0xffffd26e,0xff7cbbff,0xffff8cd5};
  for(int edge=0;edge<4;edge++){
   if(mode==0&&edge!=0||mode==1&&edge!=1&&edge!=3||mode==2&&edge!=2||mode==4&&edge!=0)continue;
   Path wire=new Path();
   for(int j=0;j<=count*5;j++){
    float t=j/(float)(count*5),u=t*w,v=t*h;
    float sag=mode==4?0:12*(float)Math.sin(t*Math.PI*6);
    float px=edge==0||edge==2?u:edge==1?12:w-12;
    float py=edge==0?24+sag:edge==2?h-24-sag:v;
    if(edge==1)px+=sag;if(edge==3)px-=sag;
    if(j==0)wire.moveTo(px,py);else wire.lineTo(px,py);
   }
   p.setColor(0xff496b59);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawPath(wire,p);p.setStyle(Paint.Style.FILL);
   for(int i=0;i<count;i++){
    float t=(i+.5f)/count,sag=12*(float)Math.sin(t*Math.PI*6);
    float px=edge==0||edge==2?t*w:edge==1?12+sag:w-12-sag;
    float py=edge==0?24+sag:edge==2?h-24-sag:t*h;
    if(mode==4){float len=12+(i%4)*10;line(c,px,py,px,py+len,0xff496b59,2);py+=len;}
    float glow=(float)(.5+.5*Math.sin(time*2.8+i*.8+edge));
    p.setColor(colors[(i+edge)%colors.length]);p.setAlpha((int)(135+120*glow));c.drawCircle(px,py,5+2*glow,p);
   }
  }
 }
 @Override protected void onDraw(Canvas c){super.onDraw(c);if(!running)return;
  long now=System.nanoTime();float dt=Math.min(.04f,Math.max(0,(now-last)/1e9f));last=now;time+=dt;
  int w=getWidth(),h=getHeight();if(w==0||h==0)return;
  int scene=getContext().getSharedPreferences("holiday",0).getInt("scene",0);
  int placement=getContext().getSharedPreferences("holiday",0).getInt("lights",0);
  lights(c,w,h,placement);
  String[] symbols={"❄","❄","❄","❄","❄","❄","❄","🎄","🎁","☃","🍬"};
  p.setStyle(Paint.Style.FILL);p.setTypeface(android.graphics.Typeface.DEFAULT);
  for(int i=0;i<N;i++){
   float factor=scene==1?.75f:scene==2?1.35f:1f;
   y[i]+=speed[i]*dt*factor;
   x[i]+=(float)Math.sin(time*1.2+drift[i])*dt*.009f;
   angle[i]+=spin[i]*dt;
   if(y[i]>1.04f){y[i]=-.04f;x[i]=rng.nextFloat();}
   if(x[i]>1.04f)x[i]=0;if(x[i]<0)x[i]=1;
   c.save();c.translate(x[i]*w,y[i]*h);c.rotate(angle[i]);
   String symbol=symbols[i%symbols.length];p.setTextSize(size[i]*(i%11<7?3:4));
   p.setColor(0xffffffff);p.setAlpha(i%3==0?170:235);
   c.drawText(symbol,0,0,p);c.restore();
  }
  postInvalidateOnAnimation();
 }
}