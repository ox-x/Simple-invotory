package com.example.uhf.fragment;

import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;

/**
 * Dashboard Fragment - 主菜单界面.
 * Shows four feature buttons: 借还, 入库, 仓库 (evenly split 1/3 width each)
 * and 高级选项 (full width at bottom).
 */
public class DashboardFragment extends KeyDwonFragment {

    private UHFMainActivity mContext;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;

        View view = getView();

        // Setup the four buttons with their respective colors and target fragments
        setupButton(view, R.id.btnCheckout, "借还",
                0xFFFF5722, CheckoutFragment.class, false);
        setupButton(view, R.id.btnStockIn, "入库",
                0xFF8BC34A, StockInFragment.class, true);
        setupButton(view, R.id.btnWarehouse, "仓库",
                0xFF673AB7, WarehouseFragment.class, false);
        setupButton(view, R.id.btnAdvanced, "高级选项",
                0xFF607D8B, AdvancedFragment.class, true);
    }

    /**
     * Configures a dashboard button: applies rounded rectangle background with given color,
     * and sets up click listener that either opens the feature directly or requires password.
     */
    private void setupButton(View parent, int btnId, final String label,
                             int color, final Class<?> fragmentClass, final boolean requiresPassword) {
        TextView btn = parent.findViewById(btnId);

        // Create rounded rectangle background with the specified color
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(16f * getResources().getDisplayMetrics().density);
        bg.setColor(color);
        btn.setBackground(bg);

        btn.setOnClickListener(v -> {
            if (requiresPassword) {
                showPasswordDialog(label, fragmentClass);
            } else {
                mContext.openFeature(fragmentClass, label);
            }
        });
    }

    // ==================== Password Dialog ====================

    private static final String ADMIN_PASSWORD = "aaa";

    private void showPasswordDialog(String featureLabel, Class<?> targetFragment) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle("管理员验证");
        builder.setMessage("请输入管理员密码以进入" + featureLabel);

        final EditText etPassword = new EditText(mContext);
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPassword.setHint("请输入密码");
        etPassword.setSingleLine(true);

        LinearLayout wrapper = new LinearLayout(mContext);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(48, 16, 48, 0);
        wrapper.addView(etPassword);
        builder.setView(wrapper);

        builder.setPositiveButton("确定", null);
        builder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());

        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String input = etPassword.getText().toString().trim();
                if (ADMIN_PASSWORD.equals(input)) {
                    dialog.dismiss();
                    mContext.openFeature(targetFragment, featureLabel);
                } else {
                    Toast.makeText(mContext, "密码错误", Toast.LENGTH_SHORT).show();
                    etPassword.setText("");
                }
            });
        });

        dialog.show();
    }

    @Override
    public void myOnKeyDwon() {
        // No action on dashboard
    }

    // ==================== Menu Item Model ====================
    // Used by AdvancedFragment

    public static class MenuItem {
        public String icon;
        public String label;
        public int color;
        public Class<?> fragmentClass;
        public boolean requiresPassword;

        public MenuItem(String icon, String label, int color, Class<?> fragmentClass) {
            this(icon, label, color, fragmentClass, false);
        }

        public MenuItem(String icon, String label, int color, Class<?> fragmentClass, boolean requiresPassword) {
            this.icon = icon;
            this.label = label;
            this.color = color;
            this.fragmentClass = fragmentClass;
            this.requiresPassword = requiresPassword;
        }
    }
}
