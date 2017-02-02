package com.septem.firstapp.product;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Created by septem on 2016/10/3.
 * 利用popupWindow创建一个popupMenu,可以显示图标和文字
 * v 1.1
 */

public class PopupMenuIcon extends PopupWindow {
    private ImageView mIcon;
    private TextView mText;
    private LinearLayout layout;
    private RelativeLayout aLine;
    private ScrollView scrollView;
    private Activity context;
    private List<String> labels;
    private List<Drawable> icons;
    private OnItemClickListener onItemClickListener = null;

    private int windowHeight = 500;
    private int windowWidth = 500;
    private int lineHeight = 150;
    private int lineWidth = 500;
    private int iconWidth = 150;
    private int iconHeight = 100;
    private int gravity;
    private int itemCount = 0;
    private int viewIdStart = 271828;
    private int textSize = 16;
    private int backgroundResource = android.R.drawable.screen_background_dark_transparent;
    private int itemBackgroundResource = android.R.drawable.screen_background_dark;
    private int linesCount = 7;

    public PopupMenuIcon(Context context) {
        super(context);

        this.context = (Activity) context;
    }

    //public FIELD
    public void setLineSize(int lineWidth,int lineHeight) {
        this.lineHeight = lineHeight;
        this.lineWidth = lineWidth;
    }
    public void setWindowSize(int windowWidth,int windowHeight) {
        this.windowHeight = windowHeight;
        this.windowWidth = windowWidth;
    }

    /**
     * 设置行高
     * @param count
     */
    public void setLinesCount(int count)
    {
        this.linesCount = count;
    }

    /**
     * 设置数据源，用于在菜单上显示信息
     * @param labels 菜单文字
     * @param icons 菜单图标
     */
    public void setData(List<String> labels, List<Drawable> icons) {
        this.labels = labels;
        this.icons = icons;
        itemCount = labels.size() > icons.size() ?
                labels.size() : icons.size();

        initView();
        updateData();
    }
    public void setBackgroundResource(int res) {
        this.backgroundResource = res;
    }

    /**
     * 设置每个菜单项的背景资源，必须在setData()之前设置.
     * 最好设置成一个selector的drawable,这样，在点击的时候会表现出不同的背景色。
     * @param res
     */
    public void setItemBackgroundResource(int res) {
        this.itemBackgroundResource = res;
    }

    /**
     * 设置点击监听器
     * @param listener
     */
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void show(View anchor, int gravity) {
        super.showAsDropDown(anchor,0,0,gravity);
    }

    //private FIELD
    private void initView() {
        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if(itemCount<=linesCount)
            setContentView(layout);
        else {
            scrollView = new ScrollView(context);
            scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            scrollView.addView(layout);
            setContentView(scrollView);
        }
    }

    private void updateData() {
        for(int i=0;i<itemCount;i++) {
            mIcon = new ImageView(context);
            mIcon.setId(1);
            mText = new TextView(context);
            mIcon.setImageDrawable(icons.get(i));
            mText.setText(labels.get(i));
            RelativeLayout.LayoutParams tvParam =
                    new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            RelativeLayout.LayoutParams ivParam =
                    new RelativeLayout.LayoutParams(iconWidth,iconHeight);
            tvParam.addRule(RelativeLayout.END_OF,1);
            tvParam.addRule(RelativeLayout.CENTER_VERTICAL);
            ivParam.addRule(RelativeLayout.CENTER_VERTICAL);
            mIcon.setLayoutParams(ivParam);
            mText.setLayoutParams(tvParam);
            mText.setGravity(Gravity.CENTER_VERTICAL);
            mText.setTextSize(textSize);

            aLine = new RelativeLayout(context);
            aLine.setClickable(true);
            aLine.setId(viewIdStart+i);
            aLine.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    lineHeight));
            aLine.setBackgroundResource(itemBackgroundResource);
            aLine.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(onItemClickListener !=null)
                        onItemClickListener.onItemClicked(v.getId()-viewIdStart);
                    dismiss();
                }
            });


            aLine.addView(mIcon);
            aLine.addView(mText);
            layout.addView(aLine);
        }

        setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        if(itemCount<=linesCount)
            setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        else setHeight(lineHeight*linesCount);

        setFocusable(true);
        setOutsideTouchable(true);
        setBackground();
    }

    private void setBackground() {
        setBackgroundDrawable(context.getResources().getDrawable(
                itemBackgroundResource
        ));
    }

    //public interface
    public interface OnItemClickListener {
        void onItemClicked(int index);
    }
}
