package com.example.uhf.activity;


import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.uhf.R;
import com.example.uhf.fragment.BlockPermalockFragment;
import com.example.uhf.fragment.BlockWriteFragment;
import com.example.uhf.fragment.CheckoutFragment;
import com.example.uhf.fragment.DashboardFragment;
import com.example.uhf.fragment.ItemDetailFragment;
import com.example.uhf.fragment.KittingFragment;
import com.example.uhf.fragment.SearchFragment;
import com.example.uhf.fragment.StockInFragment;
import com.example.uhf.fragment.WarehouseFragment;
import com.example.uhf.fragment.UHFKillFragment;
import com.example.uhf.fragment.UHFLightFragment;
import com.example.uhf.fragment.UHFLocationFragment;
import com.example.uhf.fragment.UHFLockFragment;
import com.example.uhf.fragment.UHFRadarLocationFragment;
import com.example.uhf.fragment.UHFReadTagFragment;
import com.example.uhf.fragment.UHFReadWriteFragment;
import com.example.uhf.fragment.UHFSetFragment;
import com.example.uhf.fragment.UHFUpgradeFragment;
import com.example.uhf.tools.ExportExcelAsyncTask;
import com.example.uhf.tools.UIHelper;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * UHF使用demo
 * 1、在操作设备前需要调用 init()打开设备，使用完后调用 free() 关闭设备
 * 更多函数的使用方法请查看API说明文档
 *
 * @author zhopin
 * 更新于 2020年7月23日
 */
public class UHFMainActivity extends BaseTabFragmentActivity {

    private final static String TAG = "MainActivity";
    private FragmentManager fm;
    public int selectIndex = -1;
    public ArrayList<UHFTAGInfo> tagList = new ArrayList<UHFTAGInfo>();
    public boolean loopFlag = false;
    private PlaySoundThread playSoundThread = null;
    private boolean isDashboardShowing = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Hide the title/action bar
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        checkReadWritePermission();
        initSound();
        initUHF();

        fm = getSupportFragmentManager();
        // Show dashboard initially
        if (savedInstanceState == null) {
            showDashboard();
        }
    }

    // ==================== Navigation ====================

    public void showDashboard() {
        isDashboardShowing = true;
        DashboardFragment dashboard = new DashboardFragment();
        fm.beginTransaction()
                .replace(R.id.fragment_container, dashboard, "dashboard")
                .commit();
    }

    public void openFeature(Class<?> fragmentClass, String title) {
        try {
            Fragment fragment = (Fragment) fragmentClass.newInstance();
            isDashboardShowing = false;
            fm.beginTransaction()
                    .replace(R.id.fragment_container, fragment, "feature")
                    .addToBackStack(null)
                    .commit();
        } catch (Exception e) {
            Log.e(TAG, "Error opening feature: " + fragmentClass.getSimpleName(), e);
        }
    }

    /**
     * Open the item detail page for a given EPC (read-only, default).
     */
    public void openItemDetail(String epc) {
        openItemDetail(epc, false);
    }

    /**
     * Open the item detail page for a given EPC.
     * @param fromStockIn if true, the edit button will be visible.
     */
    public void openItemDetail(String epc, boolean fromStockIn) {
        ItemDetailFragment fragment = ItemDetailFragment.newInstance(epc, fromStockIn);
        fm.beginTransaction()
                .replace(R.id.fragment_container, fragment, "item_detail")
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onBackPressed() {
        if (!isDashboardShowing) {
            // Return to dashboard
            showDashboard();
        } else {
            // Exit app
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        Log.e("zz_pp", "onDestroy()");
        releaseSoundPool();
        if (mReader != null) {
            mReader.free();
        }
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }


    @Override
    public void exportData() {
        checkReadWritePermission();
        if (loopFlag) {
            UIHelper.ToastMessage(this, R.string.uhf_msg_scaning);
            return;
        }
        if (tagList == null || tagList.isEmpty()) {
            UIHelper.ToastMessage(this, R.string.uhf_msg_export_data_empty);
            return;
        }
        new ExportExcelAsyncTask(this, tagList).execute();
    }

    HashMap<Integer, Integer> soundMap = new HashMap<Integer, Integer>();
    private SoundPool soundPool;
    private float volumnRatio;
    private AudioManager am;

    private void initSound() {
        soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 5);
        soundMap.put(1, soundPool.load(this, R.raw.barcodebeep, 1));
        soundMap.put(2, soundPool.load(this, R.raw.serror, 2));
        am = (AudioManager) this.getSystemService(AUDIO_SERVICE);

        playSoundThread = new PlaySoundThread();
        playSoundThread.start();
    }

    private void releaseSoundPool() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }

    /**
     * 播放提示音
     *
     * @param id 成功1，失败2
     */
    public void playSound(int id) {
        float audioMaxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float audioCurrentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        volumnRatio = audioCurrentVolume / audioMaxVolume;
        try {
            soundPool.play(soundMap.get(id), volumnRatio,
                    volumnRatio,
                    1,
                    0,
                    1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkReadWritePermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 0);
                finish();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 2);
            }
        }
    }

    private Toast toast;

    public void showToast(String text) {
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.show();
    }

    public void showToast(int resId) {
        showToast(getString(resId));
    }


    public void playSoundDelayed(int speed) {
        playSoundThread.play(speed);
    }


    private Object objectLock = new Object();

    private class PlaySoundThread extends Thread {
        private boolean isStop = false;
        int interval = 500;
        long lastPlayTime = SystemClock.elapsedRealtime();

        @Override
        public void run() {
            while (!isStop) {
                long start = 0;
                synchronized (objectLock) {
                    while (!isStop) {
                        if (start == 0) {
                            start = SystemClock.elapsedRealtime();
                        } else {
                            if (SystemClock.elapsedRealtime() - start >= interval) {
                                break;
                            } else {
                                SystemClock.sleep(1);
                            }
                        }
                    }
                }
                if (SystemClock.elapsedRealtime() - lastPlayTime < 500) {
                    playSound(1);
                }
            }
        }

        public void play(int speed) {
            int t = 3;
            if (speed > 85) {
                t = 3;
            } else if (speed > 66) {
                t = 100 - speed;
            } else if (speed > 33) {
                t = (100 - speed) * 2;
            } else {
                t = (100 - speed) * 3;
            }

            interval = t;
            lastPlayTime = SystemClock.elapsedRealtime();
        }

        public void stopPlay() {
            isStop = true;
            synchronized (objectLock) {
                objectLock.notifyAll();
            }
        }
    }
}
