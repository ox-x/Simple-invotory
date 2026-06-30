package com.example.uhf.tools;

import android.os.Build;
import android.util.Log;

/**
 * 模拟器/虚拟设备检测工具类。
 * 用于检测当前是否运行在Android模拟器环境中，以便自动切换为模拟模式，
 * 避免因RFID硬件API不存在而导致应用崩溃。
 *
 * 检测策略包含多层：Build特征、设备属性、运行时环境特征。
 */
public class EmulatorDetector {

    private static final String TAG = "EmulatorDetector";
    private static Boolean sIsEmulatorCache = null;

    /**
     * 判断当前是否运行在模拟器上。
     * 结果会被缓存以提高性能。
     */
    public static boolean isEmulator() {
        if (sIsEmulatorCache != null) {
            return sIsEmulatorCache;
        }

        sIsEmulatorCache = detectEmulator();
        return sIsEmulatorCache;
    }

    private static boolean detectEmulator() {
        int score = 0;

        // 方法1: 检查 Build 特征 (每条命中加分)
        score += scoreBuildProperties();

        // 方法2: 检查附加特征 (设备名、主板、序列号等)
        score += scoreAdditionalProperties();

        // 输出设备信息日志，方便排查真机误判问题
        Log.i(TAG, "Emulator score=" + score
                + " | FINGERPRINT=" + Build.FINGERPRINT
                + " | MODEL=" + Build.MODEL
                + " | PRODUCT=" + Build.PRODUCT
                + " | HARDWARE=" + Build.HARDWARE
                + " | DEVICE=" + Build.DEVICE
                + " | BOARD=" + Build.BOARD
                + " | MANUFACTURER=" + Build.MANUFACTURER
                + " | BRAND=" + Build.BRAND);

        // 需要达到足够的模拟器特征分数才判定为模拟器
        // 避免单一特征在真实设备上造成误判
        return score >= 3;
    }

    /**
     * 对 Build 属性进行评分，每条强模拟器特征 +1 分。
     * 仅使用高度可靠的模拟器特征，避免在真实UHF手持设备上误判。
     */
    private static int scoreBuildProperties() {
        int score = 0;

        String fingerprint = Build.FINGERPRINT;
        if (fingerprint != null) {
            if (fingerprint.contains("generic") || fingerprint.contains("emu")) {
                score++;
            }
            // 移除 test-keys 检测：很多真实国产设备也使用 test-keys
        }

        String model = Build.MODEL;
        if (model != null) {
            if (model.contains("google_sdk")
                    || model.contains("Emulator")
                    || model.contains("Android SDK built for x86")
                    || model.contains("sdk_gphone")
                    || model.contains("sdk_phone")
                    || model.contains("emu64")
                    || model.contains("emu32")
                    || model.equals("Android SDK built for arm64")
                    || model.contains("AOSP on")) {
                score++;
            }
        }

        String manufacturer = Build.MANUFACTURER;
        if (manufacturer != null) {
            if (manufacturer.contains("Genymotion")) {
                score++;
            }
            if (manufacturer.contains("Google")
                    && model != null && model.contains("sdk")) {
                score++;
            }
        }

        String brand = Build.BRAND;
        if (brand != null && brand.contains("generic") && model != null && model.contains("generic")) {
            score++;
        }

        String product = Build.PRODUCT;
        if (product != null) {
            if (product.contains("sdk")
                    || product.contains("emu")
                    || product.contains("vbox")
                    || product.contains("aosp")) {
                score++;
            }
            // 移除 "generic" 单独检测：一些真实手持设备 product 含 generic
        }

        String hardware = Build.HARDWARE;
        if (hardware != null) {
            if (hardware.contains("goldfish")
                    || hardware.contains("ranchu")
                    || hardware.contains("vbox")
                    || hardware.contains("qemu")) {
                score++;
            }
        }

        return score;
    }

    // 移除了 checkDeviceSpecific 方法：
    // Build.getRadioVersion() 返回 "Unknown" 在很多无基带的真实手持设备上也会出现，
    // 不适合作为模拟器判断依据。

    /**
     * 对附加 Build 属性进行评分。
     * 移除了容易在真实设备上误判的检测项：
     * - BOARD="unknown" 和 BOOTLOADER="unknown" 在真实手持设备上很常见
     * - HOST 包含 "build" 在真实设备上也很常见
     */
    private static int scoreAdditionalProperties() {
        int score = 0;

        String device = Build.DEVICE;
        if (device != null) {
            if (device.contains("generic")
                    || device.contains("emu64")
                    || device.contains("emu32")
                    || device.contains("vsoc")) {
                score++;
            }
            // 移除 "hikey" 检测：hikey 是真实开发板
        }

        // 仅检测明确的模拟器硬件标识（goldfish/ranchu）
        String board = Build.BOARD;
        if (board != null && board.contains("goldfish")) {
            score++;
        }
        if (board != null && board.contains("ranchu")) {
            score++;
        }

        return score;
    }

    // getRadioVersion 不再用于模拟器检测，但保留方法以备后用
    @SuppressWarnings("unused")
    private static String getRadioVersion() {
        try {
            return Build.getRadioVersion();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 清除缓存的结果，通常不需要调用。
     */
    public static void clearCache() {
        sIsEmulatorCache = null;
    }
}
