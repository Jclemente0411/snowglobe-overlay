package com.snowglobe.taptest;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Separate-app text editor probe; never ships inside SnowGlobe. */
public final class KeyboardProbeActivity extends Activity {
    private EditText field(LinearLayout parent,String name,int inputType){
        TextView label=new TextView(this);label.setText(name);label.setTextSize(18);parent.addView(label);
        EditText edit=new EditText(this);edit.setSingleLine(true);edit.setInputType(inputType);
        edit.setContentDescription(name+"_FIELD");edit.setHint("Tap to type");
        parent.addView(edit,new LinearLayout.LayoutParams(-1,130));return edit;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.TOP);body.setPadding(18,50,18,20);
        field(body,"PLAIN",InputType.TYPE_CLASS_TEXT);
        field(body,"PASSWORD",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field(body,"EMAIL",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        field(body,"NUMBER",InputType.TYPE_CLASS_NUMBER);
        setContentView(body);
    }
}
