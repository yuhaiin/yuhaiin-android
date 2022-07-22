package com.takisoft.preferencex.widget;

import static com.takisoft.preferencex.widget.SimpleMenuPopupWindow.DIALOG;
import static com.takisoft.preferencex.widget.SimpleMenuPopupWindow.HORIZONTAL;

import android.os.Build;
import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.AppCompatCheckedTextView;
import androidx.recyclerview.widget.RecyclerView;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class SimpleMenuListItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

    public AppCompatCheckedTextView mCheckedTextView;

    private SimpleMenuPopupWindow mWindow;

    public SimpleMenuListItemHolder(View itemView) {
        super(itemView);

        mCheckedTextView = itemView.findViewById(android.R.id.text1);
        itemView.setOnClickListener(this);
    }

    public void bind(SimpleMenuPopupWindow window, int position) {
        mWindow = window;
        mCheckedTextView.setText(mWindow.getEntries()[position]);
        mCheckedTextView.setChecked(position == mWindow.getSelectedIndex());
        mCheckedTextView.setMaxLines(mWindow.getMode() == DIALOG ? Integer.MAX_VALUE : 1);

        int padding = mWindow.listPadding[mWindow.getMode()][HORIZONTAL];
        int paddingVertical = mCheckedTextView.getPaddingTop();
        mCheckedTextView.setPadding(padding, paddingVertical, padding, paddingVertical);
    }

    @Override
    public void onClick(View view) {
        if (mWindow.getOnItemClickListener() != null) {
            mWindow.getOnItemClickListener().onClick(getAdapterPosition());
        }

        if (mWindow.isShowing()) {
            mWindow.dismiss();
        }
    }
}
