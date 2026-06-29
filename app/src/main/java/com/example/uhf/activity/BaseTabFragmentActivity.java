package com.example.uhf.activity;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.uhf.FileUtils;
import com.example.uhf.R;
import com.example.uhf.fragment.KeyDwonFragment;
import com.example.uhf.fragment.UHFReadTagFragment;
import com.example.uhf.tools.EmulatorDetector;
import com.example.uhf.tools.UHFConstants;
import com.example.uhf.tools.UIHelper;
import com.example.uhf.tools.VirtualUHFReader;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.utility.FileUtility;
import com.rscja.utility.StringUtility;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Administrator on 2015-03-10.
 */
public class BaseTabFragmentActivity extends FragmentActivity {

    private static final String TAG = "BaseTabFragmentActivity";

    public Object mReader;
    public boolean isEmulatorMode = false;
    public KeyDwonFragment currentFragment = null;
    public int TidLen = 6;
    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 检查权限是否已授予
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // 如果权限尚未授予，向用户请求权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        } else {
            // 权限已经被授予，可以执行相应操作
            // 在这里进行读取SD卡的操作
        }

    }

    public void initUHF() {
        // 检测是否运行在模拟器环境
        if (EmulatorDetector.isEmulator()) {
            isEmulatorMode = true;
            toastMessage("模拟器模式: RFID硬件不可用，UI功能可正常测试");
            Log.i(TAG, "Emulator mode activated - RFID hardware not available");
            return;
        }

        try {
            mReader = RFIDWithUHFUART.getInstance();
        } catch (Throwable ex) {
            // 捕获所有异常/错误（包括 UnsatisfiedLinkError 等）
            Log.e(TAG, "RFID init failed: " + ex.getMessage(), ex);
            // RFID硬件初始化失败时也自动进入模拟器模式，
            // 确保所有模拟器（包括未被 EmulatorDetector 覆盖的）都能正常使用
            isEmulatorMode = true;
            toastMessage("RFID硬件不可用，已切换为模拟模式");
            Log.i(TAG, "Fallback to emulator mode - RFID hardware init failed");
            return;
        }

        if (mReader != null) {
            new InitTask().execute();
        }
    }


    /**
     * ����ActionBar
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // TODO Auto-generated method stub
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
//		return super.onCreateOptionsMenu(menu);
    }


    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                break;
            case R.id.UHF_ver:
                getUHFVersion();
                break;
            case R.id.export:
                exportData();
                break;
            default:
                break;
        }
        return true;
    }

    public void exportData() {
        UHFReadTagFragment uhfReadTagFragment = new UHFReadTagFragment();
        UHFMainActivity uhfMainActivity = new UHFMainActivity();
        Date currentDate = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String currentTime = dateFormat.format(currentDate);
        String file =  "sdcard/UHF_exportData/";
        String fileName = file + currentTime;
//        FileUtils.writeFile(fileName, UHFReadTagFragment.epcTidUser, true);
        Toast.makeText(BaseTabFragmentActivity.this, "导出失败", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 单步扫描按键
        if (keyCode == 139 || keyCode == 280 || keyCode == 291 || keyCode == 293 || keyCode == 294
                || keyCode == 311 || keyCode == 312 || keyCode == 313 || keyCode == 315) {
            if (event.getRepeatCount() == 0) {
                if (currentFragment != null) {
                    currentFragment.myOnKeyDwon();
                }
            }
            return true;
        }
        // 多功能按键（key code 142）
        if (keyCode == 142) {
            if (event.getRepeatCount() == 0) {
                if (currentFragment != null) {
                    currentFragment.onMultiFunctionKey();
                }
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    public void toastMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }


    public class InitTask extends AsyncTask<String, Integer, Boolean> {
        ProgressDialog mypDialog;

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                RFIDWithUHFUART reader = (RFIDWithUHFUART) mReader;
                return reader.init(BaseTabFragmentActivity.this);
            } catch (Exception e) {
                Log.e(TAG, "init failed", e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            mypDialog.cancel();
            if (!result) {
                Toast.makeText(BaseTabFragmentActivity.this, "init fail", Toast.LENGTH_SHORT).show();
            } else {
                try {
                    RFIDWithUHFUART reader = (RFIDWithUHFUART) mReader;
                    reader.setEPCAndTIDMode();
                } catch (Exception e) {
                    Log.e(TAG, "setMode failed", e);
                }
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mypDialog = new ProgressDialog(BaseTabFragmentActivity.this);
            mypDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mypDialog.setMessage("init...");
            mypDialog.setCanceledOnTouchOutside(false);
            mypDialog.show();
        }
    }


    public boolean vailHexInput(String str) {
        if (str == null || str.length() == 0) {
            return false;
        }
        if (str.length() % 2 == 0) {
            return StringUtility.isHexNumberRex(str);
        }
        return false;
    }

    /**
     * 检查 RFID 阅读器是否就绪（非模拟器模式且已成功初始化）。
     * 如果未就绪会自动提示用户。
     */
    public boolean isReaderReady() {
        if (isEmulatorMode) {
            return false;
        }
        if (mReader == null) {
            Toast.makeText(this, R.string.uhf_msg_inventory_fail, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * 获取 RFID 阅读器（仅当就绪时），否则返回 null。
     */
    public RFIDWithUHFUART getReader() {
        if (!isEmulatorMode && mReader instanceof RFIDWithUHFUART) {
            return (RFIDWithUHFUART) mReader;
        }
        return null;
    }

    public void getUHFVersion() {
        if (isEmulatorMode) {
            String version="Software version:" + UHFConstants.EMULATOR_VERSION +
                    " \nHardware Version:" + UHFConstants.EMULATOR_HARDWARE_VERSION;
            UIHelper.alert(this, R.string.action_uhf_ver, version, R.drawable.webtext);
        } else if (mReader != null) {
            try {
                RFIDWithUHFUART reader = (RFIDWithUHFUART) mReader;
                String rfidVer = reader.getVersion();
                String hardwareVersion = reader.getHardwareVersion();
                String version = "Software version:" + rfidVer + " \nHardware Version:" + hardwareVersion;
                UIHelper.alert(this, R.string.action_uhf_ver,
                        version, R.drawable.webtext);
            } catch (Exception e) {
                toastMessage(getString(R.string.uhf_msg_read_frequency_fail));
            }
        }
    }

    public String getVerName() {
        try {
            return this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {

        }
        return "";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予，可以执行相应操作
                Log.e("TEST", "已经申请sdcard权限");
                // 在这里进行读取SD卡的操作
            } else {
                // 权限被拒绝，无法执行相应操作
                Log.e("TEST", "申请sdcard权限失败");
            }
        }
    }

}
