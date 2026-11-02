package com.snowglobe.overlay;

/** Studio appearance only. These tokens are not particle or wallpaper settings. */
public final class HolidayThemeTokens {
    public static final String CLASSIC="classic", EVERGREEN="evergreen", CANDLELIGHT="candlelight", PIXEL="pixel";
    private HolidayThemeTokens() {}
    public static String normalize(String id){
        return EVERGREEN.equals(id)||CANDLELIGHT.equals(id)||PIXEL.equals(id)?id:CLASSIC;
    }
    public static String initial(String saved,boolean legacyPixelColors){
        return saved==null?(legacyPixelColors?PIXEL:CLASSIC):normalize(saved);
    }
    public static final class Palette {
        public final boolean dark;
        public final int background,surface,raised,text,muted,primary,onPrimary,container,onContainer,secondary,onSecondary,outline;
        public Palette(boolean dark,int background,int surface,int raised,int text,int muted,int primary,int onPrimary,int container,int onContainer,int secondary,int onSecondary,int outline){
            this.dark=dark;this.background=background;this.surface=surface;this.raised=raised;this.text=text;this.muted=muted;
            this.primary=primary;this.onPrimary=onPrimary;this.container=container;this.onContainer=onContainer;
            this.secondary=secondary;this.onSecondary=onSecondary;this.outline=outline;
        }
    }
    public static Palette palette(String id){
        if(EVERGREEN.equals(id))return new Palette(true,0xff0b1b15,0xff172d23,0xff2b4433,0xfff6f4df,0xffc3d4be,
            0xffeed18f,0xff342500,0xff3c4f35,0xfff3f2d4,0xffffb8a9,0xff4b2118,0xffa3ae91);
        if(CANDLELIGHT.equals(id))return new Palette(false,0xfffff8ed,0xfffff0da,0xfff2dec8,0xff2c1919,0xff674b43,
            0xff8d2940,0xffffffff,0xfffbd8dc,0xff4c1525,0xff46623d,0xffffffff,0xff8b7065);
        return new Palette(true,0xff230c15,0xff351722,0xff4b2630,0xfffff1df,0xffe9c9bf,
            0xffffd68d,0xff3c2200,0xff60333e,0xffffe9da,0xffffb7b4,0xff4e1524,0xffc29495);
    }
    public static double contrast(int first,int second){
        double a=luminance(first),b=luminance(second);
        return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);
    }
    private static double luminance(int color){return .2126*linear((color>>16)&255)+.7152*linear((color>>8)&255)+.0722*linear(color&255);}
    private static double linear(int component){double v=component/255d;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
}
