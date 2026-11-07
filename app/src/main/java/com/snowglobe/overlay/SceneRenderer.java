package com.snowglobe.overlay;

import android.graphics.*;
import java.util.Random;

/** One Canvas scene for the preview, overlay and live wallpaper. Never owns a View. */
public final class SceneRenderer {
    private static final int CAPACITY=128, GLINTS=14, BURSTS=24, SPRITE=192;
    private static final int[][] PALETTES={
        {0xfff86457,0xff4ddf87,0xffffd37b,0xff62bfff},
        {0xffaeefff,0xffd9e8ff,0xff99b8ff,0xfff7fbff},
        {0xffffd291,0xffffe6b4,0xffffb96a,0xfffff0ca},
        {0xffff637e,0xff65e49b,0xffffd46f,0xff69bdff,0xffdf9aff}};
    private final Random random=new Random(20261102L);
    private final float[] x=new float[CAPACITY],y=new float[CAPACITY],speed=new float[CAPACITY],
        phase=new float[CAPACITY],angle=new float[CAPACITY],spin=new float[CAPACITY],sway=new float[CAPACITY];
    private final float[] gx=new float[GLINTS],gy=new float[GLINTS],ga=new float[GLINTS],gl=new float[GLINTS];
    private final float[] bx=new float[BURSTS],by=new float[BURSTS],ba=new float[BURSTS],bvX=new float[BURSTS],bvY=new float[BURSTS];
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG),bitmapPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint backgroundPaint=new Paint(Paint.ANTI_ALIAS_FLAG),snowFeather=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cable=new Path(),hill=new Path();
    private final Path[] snowCrystals={new Path(),new Path(),new Path()};
    private final Path[] snowStars={new Path(),new Path()};
    private final RectF destination=new RectF();
    private final Bitmap[] bodies=new Bitmap[5],halos=new Bitmap[5],decorations=new Bitmap[7];
    private final float[] lightX=new float[24],lightY=new float[24],lightAngle=new float[24];
    private SceneSettings settings;
    private float density,width=1,height=1,top=0,bottom=0,time=0,wake=0;
    private int palette=-1,shape=-1,lightCount=0,snowTint=0,previousActive=0;
    private long last=0;
    private boolean wallpaper=false,typingBudget=false;

    public SceneRenderer(float density,SceneSettings settings) {
        this.density=Math.max(0.5f,density);
        for(int i=0;i<CAPACITY;i++)reset(i,true);
        for(int i=0;i<GLINTS;i++){newGlint(i);ga[i]=-random.nextFloat()*7f;}
        for(int i=0;i<BURSTS;i++)ba[i]=10f;
        buildFlakes();apply(settings);
    }
    public void apply(SceneSettings value) {
        settings=value;
        if(value.reduced){wake=0;for(int i=0;i<BURSTS;i++)ba[i]=10f;}
        // The free snow/lights scene never needs decorative bitmap allocations.
        // A purchased decoration set is baked once on this settings boundary.
        if(value.decorations.length>0&&decorations[0]==null)buildDecorations();
        if(palette!=value.palette||shape!=value.bulbShape){palette=value.palette;shape=value.bulbShape;buildBulbs();}
        int tint=SnowVisualPolicy.color(value.snowShade);
        if(snowTint!=tint){
            snowTint=tint;
            // One reusable unit-radius gradient. The Canvas supplies the scale;
            // no blur filter, bitmap or gradient is allocated during animation.
            snowFeather.setShader(new RadialGradient(0,0,1,
                new int[]{tint,tint,(tint&0x00ffffff)|0x60000000,tint&0x00ffffff},
                new float[]{0,.28f,.63f,1},Shader.TileMode.CLAMP));
        }
        layoutLights();
    }
    public SceneSettings settings(){return settings;}
    public void setWallpaper(boolean value){wallpaper=value;}
    /** Bound overlay work while preserving particles throughout the full canvas. */
    public void setTypingBudget(boolean value){typingBudget=value;}
    public void resize(int w,int h,float safeTop,float safeBottom,float pixelDensity) {
        float d=Math.max(.5f,pixelDensity);
        if(width==w&&height==h&&top==safeTop&&bottom==safeBottom&&density==d)return;
        width=Math.max(1,w);height=Math.max(1,h);top=Math.max(0,safeTop);bottom=Math.max(0,safeBottom);density=d;
        backgroundPaint.setShader(new LinearGradient(0,0,0,height,0xff0b2037,0xff173b4b,Shader.TileMode.CLAMP));
        hill.reset();hill.moveTo(0,height);
        hill.lineTo(0,height*.85f);hill.cubicTo(width*.3f,height*.7f,width*.65f,height*.95f,width,height*.82f);
        hill.lineTo(width,height);hill.close();layoutLights();
    }
    public void resetClock(){last=0;}
    public void wake(){resetClock();if(settings.wakeSnow&&settings.snowVisibility>0&&!settings.reduced){wake=3f;for(int i=0;i<16;i++){y[i]=random.nextFloat()*.35f;x[i]=random.nextFloat();}}}
    public boolean animated(){
        // A sparkle-only scene is stationary while typing; do not keep scheduling
        // invisible glints. Snow and strand animation still use their bounded budget.
        return wake>0||hasBurst()||!settings.reduced&&(activeParticles()>0
            ||settings.lights&&settings.motion!=0&&settings.brightness>0
            ||!typingBudget&&settings.sparks&&settings.sparkDensity>0);
    }
    private boolean hasBurst(){for(float a:ba)if(a<2.2f)return true;return false;}
    public void touch(float px,float py) {
        if(settings.reduced)return;
        for(int i=0;i<BURSTS;i++){bx[i]=px;by[i]=py;ba[i]=0;double r=random.nextDouble()*Math.PI*2;
            float v=dp(18+random.nextFloat()*40);bvX[i]=(float)Math.cos(r)*v;bvY[i]=(float)Math.sin(r)*v;}
    }
    private float dp(float n){return n*density;}
    private void reset(int i,boolean initial) {
        x[i]=random.nextFloat();y[i]=random.nextFloat();speed[i]=.6f+random.nextFloat()*.8f;
        phase[i]=random.nextFloat()*6.283185f;angle[i]=random.nextFloat()*360;
        spin[i]=(random.nextFloat()-.5f)*58f;
        sway[i]=MotionMath.snowSway(time,phase[i],SnowVisualPolicy.depth(i));
        if(initial){
            // A softly jittered two-dimensional low-discrepancy field fills the
            // bottom quarter immediately, including at the reduced IME budget.
            // The two steps are independent; flakes do not line up diagonally.
            x[i]=(.11f+i*.7548777f+x[i]*.10f)%1f;
            y[i]=(.07f+i*.5698403f+y[i]*.08f)%1f;
        }
        if(initial||settings==null)return;
        float dx=MotionMath.directionX(settings.direction),dy=MotionMath.directionY(settings.direction);
        float fluxX=Math.abs(dx)*height,fluxY=Math.abs(dy)*width;
        float marginX=dp(32)/width,marginY=dp(32)/height;
        if(random.nextFloat()*(fluxX+fluxY)<fluxX)x[i]=dx>0?-marginX:1+marginX;
        else y[i]=dy>0?-marginY:1+marginY;
    }
    private void newGlint(int i){gx[i]=.08f+random.nextFloat()*.84f;gy[i]=.12f+random.nextFloat()*.73f;gl[i]=3f+random.nextFloat()*2.5f;}
    private int activeParticles() {
        boolean snowVisible=(settings.snow||wallpaper&&settings.wallpaperSnow||wallpaper&&wake>0)
            &&settings.snowVisibility>0f;
        boolean decorationVisible=settings.decorations.length>0;
        boolean wallpaperSnow=wallpaper&&settings.wallpaperSnow&&settings.snowVisibility>0f;
        boolean wakeSnow=wallpaper&&wake>0&&settings.snowVisibility>0f;
        int count=SnowVisualPolicy.particleCount(settings.count,snowVisible,decorationVisible,
            wallpaperSnow,wakeSnow,settings.edgeOnly&&!wallpaper);
        return typingBudget?Math.min(count,36):count;
    }
    public void advance(long frameNanos) {
        float dt=MotionMath.seconds(last,frameNanos);last=frameNanos;time+=dt;wake=Math.max(0,wake-dt);
        if(settings.reduced)return;
        float dx=MotionMath.directionX(settings.direction),dy=MotionMath.directionY(settings.direction);
        int active=activeParticles();
        if(dt==0){
            for(int i=previousActive;i<active;i++)sway[i]=MotionMath.snowSway(time,phase[i],SnowVisualPolicy.depth(i));
            previousActive=active;return;
        }
        for(int i=0;i<active;i++) {
            float depth=SnowVisualPolicy.depth(i);
            float v=dp(35)*settings.speed*speed[i]*depth*(settings.gentle?.72f:1f);
            float currentSway=MotionMath.snowSway(time,phase[i],depth);
            float drift=(i<previousActive?currentSway-sway[i]:0)*dp(12+depth*10)*settings.wind*(settings.gentle?.75f:1f);
            sway[i]=currentSway;
            // Drift acts across the selected travel direction, including sideways
            // and upward scenes, without changing the user's speed or direction.
            x[i]+=(v*dx*dt+dy*drift)/width;y[i]+=(v*dy*dt-dx*drift)/height;
            angle[i]=(angle[i]+spin[i]*settings.spin*dt*(settings.gentle?.6f:1f)+360)%360;
            if(x[i]<-dp(40)/width||x[i]>1+dp(40)/width||y[i]<-dp(40)/height||y[i]>1+dp(40)/height)reset(i,false);
        }
        previousActive=active;
        if(settings.sparks&&!typingBudget)for(int i=0;i<GLINTS;i++){ga[i]+=dt;if(ga[i]>gl[i]+2f){newGlint(i);ga[i]=-random.nextFloat()*2.5f;}}
        for(int i=0;i<BURSTS;i++)if(ba[i]<2.2f){ba[i]+=dt;bx[i]+=bvX[i]*dt;by[i]+=bvY[i]*dt;bvY[i]+=dp(12)*dt;}
    }
    private void fill(int color,int alpha){ink.setShader(null);ink.setStyle(Paint.Style.FILL);ink.setColor(color);ink.setAlpha(Math.max(0,Math.min(255,alpha)));}
    private void line(int color,float width,int alpha){fill(color,alpha);ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(width);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);}
    private void buildFlakes(){
        for(int i=0;i<6;i++){double a=i*Math.PI/3;float dx=(float)Math.cos(a),dy=(float)Math.sin(a);
            for(int variant=0;variant<snowCrystals.length;variant++){
                Path crystal=snowCrystals[variant];
                crystal.moveTo(0,0);crystal.lineTo(dx,dy);
                int branches=variant==1?1:variant==2?3:2;
                for(int branch=0;branch<branches;branch++){
                    float at=variant==1?.66f:variant==2?.40f+branch*.21f:.52f+branch*.26f;
                    float reach=variant==2?.13f:.19f,spread=variant==1?.23f:.17f;
                    crystal.moveTo(dx*at,dy*at);crystal.lineTo(dx*(at-reach)-dy*spread,dy*(at-reach)+dx*spread);
                    crystal.moveTo(dx*at,dy*at);crystal.lineTo(dx*(at-reach)+dy*spread,dy*(at-reach)-dx*spread);
                }
            }
            for(Path star:snowStars){star.moveTo(0,0);star.lineTo(dx,dy);}
            // Fine hexagonal facets vary the silhouette without using glyph fonts.
            float nx=(float)Math.cos(a+Math.PI/3),ny=(float)Math.sin(a+Math.PI/3);
            snowCrystals[1].moveTo(dx*.31f,dy*.31f);snowCrystals[1].lineTo(nx*.31f,ny*.31f);
            snowStars[1].moveTo(dx*.41f,dy*.41f);snowStars[1].lineTo(nx*.41f,ny*.41f);
        }
    }
    private void layoutLights(){
        if(settings==null)return;
        lightCount=Math.max(6,Math.min(24,(int)(width/dp(34*settings.bulbSize))));
        float span=width/(settings.design==1?3f:2f),base=top+dp(9),sag=dp(settings.design==1?20:settings.design==2?0:10);
        cable.reset();cable.moveTo(0,base);
        int segments=settings.design==1?3:2;
        for(int n=0;n<segments;n++)cable.quadTo(span*(n+.5f),base+2*sag,span*(n+1),base);
        for(int i=0;i<lightCount;i++){
            float px=(i+.5f)*width/lightCount,t=(px%span)/span;
            lightX[i]=px;lightY[i]=base+4*t*(1-t)*sag;
            lightAngle[i]=(float)Math.toDegrees(Math.atan2(4*sag*(1-2*t),span))*.24f;
        }
    }
    private void buildBulbs(){
        int[] colors=PALETTES[palette];
        for(int i=0;i<colors.length;i++){
            int col=colors[i];Bitmap body=Bitmap.createBitmap(SPRITE,SPRITE,Bitmap.Config.ARGB_8888);
            Bitmap halo=Bitmap.createBitmap(SPRITE,SPRITE,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(body),g=new Canvas(halo);c.scale(2,2);g.scale(2,2);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new RadialGradient(48,48,37,new int[]{(col&0xffffff)|0x99000000,(col&0xffffff)|0x40000000,col&0xffffff},new float[]{0,.30f,1},Shader.TileMode.CLAMP));
            g.drawCircle(48,48,37,p);p.setShader(null);
            p.setColor(0xff112b25);c.drawRoundRect(42,22,54,33,3,3,p);
            p.setColor(0xff8c9f8d);c.drawRoundRect(43,28,53,33,1,1,p);
            Path glass=new Path();
            if(shape==1)glass.addOval(35,33,61,61,Path.Direction.CW);
            else if(shape==2)glass.addRoundRect(43,33,53,52,4,4,Path.Direction.CW);
            else {glass.moveTo(42,32);glass.cubicTo(28,45,39,66,48,71);glass.cubicTo(57,66,68,45,54,32);glass.close();}
            p.setShader(new LinearGradient(34,35,62,64,new int[]{0xfff9ffff,col,col,0xff172c3d},new float[]{0,.24f,.66f,1},Shader.TileMode.CLAMP));
            c.drawPath(glass,p);p.setShader(null);
            // Fine glass edge and lower reflection are baked once into this sprite.
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.25f);p.setColor(0x77ffffff);
            c.drawPath(glass,p);p.setStyle(Paint.Style.FILL);
            p.setColor(0x42ffffff);c.drawOval(41,35,45,44,p);
            p.setColor(0x55ffffff);c.drawOval(52,53,55,58,p);
            p.setColor(0xfffff2cc);p.setAlpha(220);c.drawRoundRect(46.8f,40,49.2f,shape==2?48:56,1.1f,1.1f,p);
            p.setColor(0xaaffffff);p.setStrokeWidth(1.6f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
            if(shape==2)c.drawLine(45,36,45,42,p);else c.drawLine(40,39,38,48,p);
            bodies[i]=body;halos[i]=halo;
        }
        for(int i=colors.length;i<bodies.length;i++){bodies[i]=null;halos[i]=null;}
    }
    private void drawBulb(Canvas c,float px,float py,float unit,float rotation,int index,float energy,float opacity){
        int i=index%PALETTES[palette].length;
        c.save();c.translate(px,py);c.rotate(rotation);
        destination.set(-48*unit,-24*unit,48*unit,72*unit);
        bitmapPaint.setAlpha((int)(255*settings.glow*energy*opacity));c.drawBitmap(halos[i],null,destination,bitmapPaint);
        bitmapPaint.setAlpha((int)(255*(.30f+.70f*energy)*opacity));c.drawBitmap(bodies[i],null,destination,bitmapPaint);
        c.restore();
    }
    private void buildDecorations(){
        for(int type=0;type<7;type++){
            Bitmap bitmap=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bitmap);c.translate(64,64);c.scale(3,3);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStrokeCap(Paint.Cap.ROUND);Path path=new Path();
            switch(type){
                case 0:
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(0xfffff7eb);
                    path.moveTo(-4,15);path.lineTo(-4,-7);path.cubicTo(-4,-20,12,-20,12,-7);c.drawPath(path,p);
                    p.setColor(0xffec4d5a);p.setStrokeWidth(2.8f);for(int k=0;k<5;k++)c.drawLine(-6,12-k*4,-2,10-k*4,p);c.drawLine(4,-15,8,-12,p);break;
                case 1:
                    p.setColor(0xffc94b61);c.drawRoundRect(-13,-9,13,13,2,2,p);p.setColor(0xfff15c73);c.drawRect(-14,-10,14,-5,p);
                    p.setColor(0xffffd991);c.drawRect(-2,-10,2,13,p);c.drawRect(-13,0,13,3,p);
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawOval(-10,-17,0,-9,p);c.drawOval(0,-17,10,-9,p);break;
                case 2:
                    p.setColor(0xff54b783);path.moveTo(0,-18);path.lineTo(-13,10);path.lineTo(13,10);path.close();c.drawPath(path,p);
                    p.setColor(0xff9c6848);c.drawRoundRect(-3,10,3,16,1,1,p);p.setColor(0xffffd176);c.drawCircle(0,-18,2.5f,p);
                    p.setColor(0xffff6c6c);c.drawCircle(-4,2,1.8f,p);c.drawCircle(5,6,1.8f,p);break;
                case 3:
                    p.setColor(0xfff3fbff);c.drawCircle(0,7,10,p);c.drawCircle(0,-7,7,p);
                    p.setColor(0xff19324c);c.drawRect(-6,-16,6,-12,p);c.drawRect(-9,-12,9,-10,p);c.drawCircle(-2,-7,1,p);c.drawCircle(2,-7,1,p);
                    p.setColor(0xfff6a85e);path.moveTo(0,-5);path.lineTo(7,-3);path.lineTo(0,-2);path.close();c.drawPath(path,p);
                    p.setColor(0xffde5b71);c.drawRoundRect(-7,-1,7,2,1,1,p);c.drawRect(3,2,6,9,p);break;
                case 5:
                    p.setColor(0xffffd788);for(int k=0;k<10;k++){double a=-Math.PI/2+k*Math.PI/5;float r=k%2==0?16:7;float px=(float)Math.cos(a)*r,py=(float)Math.sin(a)*r;if(k==0)path.moveTo(px,py);else path.lineTo(px,py);}path.close();c.drawPath(path,p);break;
                default:p.setColor(0xffee93bf);c.rotate(25);c.drawRoundRect(-3,-8,3,8,2,2,p);break;
            }
            decorations[type]=bitmap;
        }
    }
    private void drawFlake(Canvas c,float px,float py,float radius,float rotation,int alpha,int variant,int layer){
        int opacity=SnowVisualPolicy.alpha(alpha,settings.snowVisibility);
        if(opacity<=0)return;
        c.save();c.translate(px,py);c.rotate(rotation);c.scale(radius,radius);
        if(settings.flake==0){
            snowFeather.setAlpha(opacity);c.drawCircle(0,0,1, snowFeather);
            fill(snowTint,Math.round(opacity*.48f));c.drawCircle(0,0,.29f,ink);
        }else{
            if(layer==3){
                // Sparse foreground softness gives depth, not a screen-wide bloom.
                // The selected charcoal/silver tint also applies to this soft edge.
                snowFeather.setAlpha(Math.round(opacity*.21f));c.drawCircle(0,0,1,snowFeather);
            }
            line(snowTint,layer==0?.19f:.125f,opacity);
            c.drawPath(settings.flake==1?snowCrystals[variant%snowCrystals.length]:snowStars[variant%snowStars.length],ink);
            if(layer>=2){fill(snowTint,opacity);c.drawCircle(0,0,.10f,ink);}
        }
        c.restore();
    }
    public void draw(Canvas c,boolean background){
        if(background){c.drawRect(0,0,width,height,backgroundPaint);fill(0xff112b3e,255);c.drawPath(hill,ink);}
        int count=activeParticles();
        boolean snow=settings.snow&&settings.snowVisibility>0;
        if(wallpaper&&settings.wallpaperSnow&&settings.snowVisibility>0)snow=true;
        if(wake>0&&wallpaper&&settings.snowVisibility>0)snow=true;
        int total=settings.decorations.length;
        // Back-to-front order is stable across frames; all arrays and paths are
        // retained. Four tiny bounded passes avoid sorting or per-frame objects.
        for(int layer=0;layer<4;layer++)for(int i=0;i<count;i++){
            if(SnowVisualPolicy.layer(i)!=layer)continue;
            float depth=SnowVisualPolicy.depth(i),px=x[i]*width,py=y[i]*height;
            float rotation=settings.reduced||settings.spin==0?0:angle[i];
            if(px<-dp(65)||px>width+dp(65)||py<-dp(80)||py>height+dp(80))continue;
            if(total>0&&i%4==0){int type=settings.decorations[(i/4)%total];
                if(type==4){float e=settings.brightness*MotionMath.brightness(settings.fallingBlink?2:0,time,i,settings.tempo,settings.reduced);
                    drawBulb(c,px,py,dp(.47f)*settings.size*depth,rotation,i,e,.95f);}
                else {float r=dp(13)*settings.size*depth;c.save();c.translate(px,py);c.rotate(rotation);destination.set(-r,-r,r,r);bitmapPaint.setAlpha(Math.min(255,(int)(220*depth/1.2f)));c.drawBitmap(decorations[type],null,destination,bitmapPaint);c.restore();}
            }else if(snow)drawFlake(c,px,py,dp(4.0f)*settings.size*depth*(.90f+speed[i]*.12f),
                rotation,SnowVisualPolicy.opacity(i),i,layer);
        }
        if(settings.lights){line(0xff091c19,dp(3.3f),255);c.drawPath(cable,ink);line(0xff66876e,dp(.8f),220);c.drawPath(cable,ink);
            for(int i=0;i<lightCount;i++)drawBulb(c,lightX[i],lightY[i],dp(.58f)*settings.bulbSize,lightAngle[i],i,
                settings.brightness*MotionMath.brightness(settings.motion,time,i,settings.tempo,settings.reduced),1);
        }
        if(settings.sparks&&!settings.reduced&&!typingBudget){int n=Math.round(GLINTS*settings.sparkDensity);
            for(int i=0;i<n;i++){float a=MotionMath.envelope(ga[i],gl[i]);if(a<.01f)continue;
                float py=top+dp(42)+gy[i]*Math.max(1,height-top-bottom-dp(90));
                if(i%3!=0)drawBulb(c,gx[i]*width,py,dp(.45f),-12+i*3,i,settings.brightness,a*.85f);
                else {fill(PALETTES[palette][i%PALETTES[palette].length],(int)(200*a));float r=dp(5)*a;float xx=gx[i]*width;c.drawOval(xx-r,py-dp(.9f),xx+r,py+dp(.9f),ink);c.drawOval(xx-dp(.9f),py-r,xx+dp(.9f),py+r,ink);}
            }
        }
        for(int i=0;i<BURSTS;i++)if(ba[i]<2.2f)drawFlake(c,bx[i],by[i],dp(2.8f),angle[i],(int)(230*(1-ba[i]/2.2f)),i,2);
    }
}
