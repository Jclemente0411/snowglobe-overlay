package com.snowglobe.overlay;
import android.content.Context;
import android.view.View;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import java.util.Random;
public class SnowView extends View {
 private final Paint p=new Paint(3); private final Random random=new Random(42); private final float[] x=new float[85],y=new float[85],speed=new float[85],radius=new float[85]; private long last=System.nanoTime();private float phase=0;
 public SnowView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);for(int i=0;i<x.length;i++){x[i]=random.nextFloat();y[i]=random.nextFloat();speed[i]=0.025f+random.nextFloat()*0.085f;radius[i]=1.4f+random.nextFloat()*3.2f;}}
 @Override protected void onDraw(Canvas c){super.onDraw(c);long now=System.nanoTime();float dt=Math.min(.05f,(now-last)/1000000000f);last=now;phase+=dt;int w=getWidth(),h=getHeight();
 p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0xff29552e);c.drawLine(0,22,w,22,p);p.setStyle(Paint.Style.FILL);
 int[] colors={0xffff3838,0xff35ee65,0xffffd247,0xff49aaff,0xffff70cf};
 for(int i=0;i<17;i++){float bx=(i+.5f)*w/17f;float by=25+(i%2)*12;float blink=(float)(.35+.65*Math.pow(Math.sin(phase*2.8+i*1.7),2));p.setColor(colors[i%colors.length]);p.setAlpha((int)(255*blink));c.drawCircle(bx,by,7,p);}
 p.setColor(Color.WHITE);
 for(int i=0;i<x.length;i++){y[i]+=speed[i]*dt;x[i]+=(float)Math.sin(phase+i)*dt*.003f;if(y[i]>1.03f)y[i]=-.03f;if(x[i]>1.03f)x[i]=0;if(x[i]<0)x[i]=1;c.drawCircle(x[i]*w,y[i]*h,radius[i],p);}
 postInvalidateDelayed(33);
 }
}