package com.snowglobe.taptest;
import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
/** Separate UID. Not packaged in SnowGlobe or uploaded to Google Play. */
public final class TapProbeActivity extends Activity {
    private int normal,secure;
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);root.setPadding(30,100,30,100);
        EditText typing=new EditText(this);typing.setHint("TYPE HERE");
        typing.setSingleLine(true);typing.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        root.addView(typing,new LinearLayout.LayoutParams(-1,120));
        Button ordinary=new Button(this);ordinary.setText("NORMAL 0");ordinary.setOnClickListener(v->ordinary.setText("NORMAL "+(++normal)));root.addView(ordinary,new LinearLayout.LayoutParams(-1,160));
        Button protectedButton=new Button(this);protectedButton.setText("PROTECTED 0");protectedButton.setFilterTouchesWhenObscured(true);protectedButton.setOnClickListener(v->protectedButton.setText("PROTECTED "+(++secure)));root.addView(protectedButton,new LinearLayout.LayoutParams(-1,160));
        setContentView(root);
    }
}
