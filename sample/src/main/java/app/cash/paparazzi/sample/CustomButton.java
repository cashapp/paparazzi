package app.cash.paparazzi.sample;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.widget.AppCompatButton;


public class CustomButton extends AppCompatButton implements View.OnTouchListener {

  private TypedArray typedArray;
  private int textColor;
  private ColorStateList colorPressedTextColor;
  private String buttonText = "";


  public CustomButton(Context context) {
    super(context);
    setStyle(null, R.style.CustomButton);
  }

  public CustomButton(Context context, AttributeSet attrs) {
    super(context, attrs);
    setStyle(attrs, R.style.CustomButton);
  }

  public CustomButton(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setStyle(attrs, defStyleAttr);
  }

  public void setStyle(AttributeSet attrs, int defStyle) {
    typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.CustomButton, defStyle, defStyle);
    init(typedArray);
    typedArray.recycle();
  }

  private void init(TypedArray typedArrayAttr) {
    textColor = typedArrayAttr.getColor(R.styleable.CustomButton_customButtonTextColor, R.attr.customColor);
    buttonText = typedArrayAttr.getString(R.styleable.CustomButton_customButtonText);
    colorPressedTextColor = typedArrayAttr.getColorStateList(R.styleable.CustomButton_customButtonPressedTextColor);
    notifyChanges();
  }

  private void notifyChanges() {
    setOnTouchListener(this);
    setButtonText(buttonText);
    this.setTextColor(textColor);
  }

  @Override
  public boolean performClick() {
    setOnTouchListener(this);
    return super.performClick();
  }

  public void setButtonText(String btnText) {
    if (TextUtils.isEmpty(btnText)) {
      return;
    }

    this.buttonText = btnText;
    this.setText(buttonText);
    this.setContentDescription(buttonText);
  }

  @Override
  public boolean onTouch(View v, MotionEvent event) {
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        this.setTextColor(colorPressedTextColor);
        break;
      case MotionEvent.ACTION_UP:
        this.setTextColor(textColor);
        break;
      case MotionEvent.ACTION_CANCEL:
        this.setTextColor(textColor);
        break;
    }
    return false;
  }
}
