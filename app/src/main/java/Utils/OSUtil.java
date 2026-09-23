package Utils;

import android.os.Build;
import android.text.TextUtils;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * 设备系统与定制 ROM 识别工具类 (兼容 Android 10+ / HarmonyOS / HyperOS / ColorOS / OriginOS)
 */
public class OSUtil {

    // 小米 MIUI & HyperOS
    private static final String KEY_MIUI_VERSION_NAME = "ro.miui.ui.version.name";
    private static final String KEY_HYPEROS_VERSION_NAME = "ro.mi.os.version.name";

    // 华为 EMUI & 鸿蒙 HarmonyOS
    private static final String KEY_EMUI_VERSION_CODE = "ro.build.version.emui";
    private static final String KEY_HARMONYOS_VERSION = "hw_sc.build.platform.version";

    // 魅族 Flyme
    private static final String KEY_FLYME_PUBLISH_FLAG = "ro.flyme.published";
    private static final String KEY_FLYME_SETUP_FLAG = "ro.meizu.setupwizard.flyme";

    // OPPO ColorOS
    private static final String KEY_COLOROS_VERSION = "ro.build.version.opporom";

    // vivo OriginOS / FuntouchOS
    private static final String KEY_ORIGINOS_VERSION = "ro.vivo.os.version";

    // 荣耀 MagicOS
    private static final String KEY_MAGICOS_VERSION = "ro.build.version.magic";

    /**
     * 是否是小米系统 (MIUI 或 HyperOS 澎湃)
     */
    public static boolean isXiaomi() {
        if (checkManufacturer("xiaomi", "redmi", "blackshark")) {
            return true;
        }
        return !TextUtils.isEmpty(getSystemProperty(KEY_MIUI_VERSION_NAME))
                || !TextUtils.isEmpty(getSystemProperty(KEY_HYPEROS_VERSION_NAME));
    }

    /**
     * 是否是华为系统 (EMUI 或 HarmonyOS)
     */
    public static boolean isHuawei() {
        if (checkManufacturer("huawei")) {
            return true;
        }
        return !TextUtils.isEmpty(getSystemProperty(KEY_EMUI_VERSION_CODE))
                || !TextUtils.isEmpty(getSystemProperty(KEY_HARMONYOS_VERSION));
    }

    /**
     * 是否是荣耀系统 (独立后的 MagicOS)
     */
    public static boolean isHonor() {
        if (checkManufacturer("honor")) {
            return true;
        }
        return !TextUtils.isEmpty(getSystemProperty(KEY_MAGICOS_VERSION));
    }

    /**
     * 是否是魅族 Flyme
     */
    public static boolean isFlyme() {
        if (checkManufacturer("meizu")) {
            return true;
        }
        if (!TextUtils.isEmpty(getSystemProperty(KEY_FLYME_PUBLISH_FLAG))
                || !TextUtils.isEmpty(getSystemProperty(KEY_FLYME_SETUP_FLAG))) {
            return true;
        }
        String display = Build.DISPLAY;
        return display != null && display.toLowerCase(Locale.ROOT).contains("flyme");
    }

    /**
     * 是否是 OPPO / 一加 (ColorOS)
     */
    public static boolean isOppo() {
        if (checkManufacturer("oppo", "oneplus", "realme")) {
            return true;
        }
        return !TextUtils.isEmpty(getSystemProperty(KEY_COLOROS_VERSION));
    }

    /**
     * 是否是 vivo / iQOO (OriginOS)
     */
    public static boolean isVivo() {
        if (checkManufacturer("vivo", "iqoo")) {
            return true;
        }
        return !TextUtils.isEmpty(getSystemProperty(KEY_ORIGINOS_VERSION));
    }

    // 保持向后兼容的方法名
    public static boolean isMIUI() {
        return isXiaomi();
    }

    public static boolean isEMUI() {
        return isHuawei();
    }

    private static boolean checkManufacturer(String... names) {
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase(Locale.ROOT) : "";
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase(Locale.ROOT) : "";
        for (String name : names) {
            if (manufacturer.contains(name) || brand.contains(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 反射获取系统属性
     */
    private static String getSystemProperty(String key) {
        try {
            Class<?> clz = Class.forName("android.os.SystemProperties");
            Method getMethod = clz.getMethod("get", String.class);
            return (String) getMethod.invoke(clz, key);
        } catch (Exception ignored) {
            return "";
        }
    }
}