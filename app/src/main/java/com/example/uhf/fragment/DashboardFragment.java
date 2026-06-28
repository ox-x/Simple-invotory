package com.example.uhf.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;

/**
 * Dashboard Fragment - 主菜单界面.
 * Shows four feature cards in a 2x2 grid: 借还, 入库, 仓库, 高级选项.
 * Uses Material3 styled cards with feature-specific colors.
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

        // Setup card click listeners (background colors are set in XML)
        setupCard(view, R.id.btnCheckout, CheckoutFragment.class, false);
        setupCard(view, R.id.btnStockIn, StockInFragment.class, true);
        setupCard(view, R.id.btnWarehouse, WarehouseFragment.class, false);
        setupCard(view, R.id.btnAdvanced, AdvancedFragment.class, true);
    }

    private void setupCard(View parent, int cardId, final Class<?> fragmentClass, final boolean requiresPassword) {
        View card = parent.findViewById(cardId);
        card.setOnClickListener(v -> {
            if (requiresPassword) {
                String featureLabel = getFeatureLabel(cardId);
                showPasswordDialog(featureLabel, fragmentClass);
            } else {
                mContext.openFeature(fragmentClass, "");
            }
        });
    }

    private String getFeatureLabel(int cardId) {
        if (cardId == R.id.btnCheckout) return getString(R.string.tab_borrow_return);
        if (cardId == R.id.btnStockIn) return getString(R.string.tab_stockin);
        if (cardId == R.id.btnWarehouse) return getString(R.string.tab_warehouse);
        if (cardId == R.id.btnAdvanced) return getString(R.string.tab_advanced);
        return "";
    }

    // ==================== Password Management ====================

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_ADMIN_PASSWORD = "admin_password";
    private static final String KEY_RESET_TOKEN = "reset_token_applied";
    private static final String DEFAULT_PASSWORD = "aaa";

    /**
     * 密码重置令牌。
     * 正常情况保持空字符串（无效）。
     * 忘记密码时，将此值改为任意非空字符串（如 "reset"），重新编译安装即可重置密码。
     * 重置后请改回空字符串。
     */
    private static final String RESET_TOKEN = "reset";

    /**
     * 启动时调用，检测 RESET_TOKEN 是否已更改。
     * 若非空且与上次记录的令牌不同，则重置密码为默认值。
     */
    public static void checkPasswordReset(Context context) {
        if (RESET_TOKEN == null || RESET_TOKEN.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String lastToken = prefs.getString(KEY_RESET_TOKEN, "");
        if (!RESET_TOKEN.equals(lastToken)) {
            prefs.edit()
                    .putString(KEY_ADMIN_PASSWORD, DEFAULT_PASSWORD)
                    .putString(KEY_RESET_TOKEN, RESET_TOKEN)
                    .apply();
        }
    }

    public static String getAdminPassword(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_ADMIN_PASSWORD, DEFAULT_PASSWORD);
    }

    public static void setAdminPassword(Context context, String newPassword) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_ADMIN_PASSWORD, newPassword).apply();
    }

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
        builder.setNeutralButton("忘记密码？重置", null);

        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String input = etPassword.getText().toString().trim();
                if (getAdminPassword(mContext).equals(input)) {
                    dialog.dismiss();
                    mContext.openFeature(targetFragment, featureLabel);
                } else {
                    Toast.makeText(mContext, "密码错误", Toast.LENGTH_SHORT).show();
                    etPassword.setText("");
                }
            });

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                showResetPasswordConfirmationDialog(dialog, featureLabel, targetFragment);
            });
        });

        dialog.show();
    }

    /**
     * 显示重置密码确认对话框。
     */
    private void showResetPasswordConfirmationDialog(AlertDialog passwordDialog, String featureLabel, Class<?> targetFragment) {
        new AlertDialog.Builder(mContext)
                .setTitle("重置密码")
                .setMessage("确定要将密码重置为默认值吗？")
                .setPositiveButton("确定", (confirmDialog, which) -> {
                    setAdminPassword(mContext, DEFAULT_PASSWORD);
                    Toast.makeText(mContext, "密码已重置为默认值", Toast.LENGTH_SHORT).show();
                    passwordDialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
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
