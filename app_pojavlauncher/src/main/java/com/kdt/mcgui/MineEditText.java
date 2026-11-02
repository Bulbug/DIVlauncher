package com.kdt.mcgui;

import android.content.*;
import android.util.*;
import android.graphics.*;
import android.widget.EditText;

public class MineEditText extends androidx.appcompat.widget.AppCompatEditText {
	public MineEditText(Context ctx) {
		super(ctx);
		init();
	}

	public MineEditText(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		init();
	}

	public void init() {
		setBackgroundResource(net.kdt.pojavlaunch.R.drawable.field_background);
		int pad = getResources().getDimensionPixelSize(net.kdt.pojavlaunch.R.dimen.padding_moderate);
		setPadding(pad, pad, pad, pad);
		setTextColor(getResources().getColor(net.kdt.pojavlaunch.R.color.primary_text));
		setHintTextColor(getResources().getColor(net.kdt.pojavlaunch.R.color.secondary_text));
	}
}
