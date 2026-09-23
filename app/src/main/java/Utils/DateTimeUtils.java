package Utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateTimeUtils {

    private static final int SECOND_MILLIS = 1000;
    private static final int MINUTE_MILLIS = 60 * SECOND_MILLIS;
    private static final int HOUR_MILLIS = 60 * MINUTE_MILLIS;
    private static final int DAY_MILLIS = 24 * HOUR_MILLIS;

    /**
     * 计算友好显示时间（如刚刚、1分钟前、昨天、09-19、2025-09-19）
     * @param time 毫秒时间戳（13位）
     */
    public static String getTimeAgo(long time) {
        long now = System.currentTimeMillis();

        // 兼容处理：如果误传了 10 位秒级时间戳，自动转换为 13 位毫秒
        if (time > 0 && time < 100000000000L) {
            time *= 1000;
        }

        if (time <= 0) {
            return "未知时间";
        }

        // 网络微秒级延迟或未来时间平滑兜底
        if (time > now) {
            return "刚刚";
        }

        // 今天 00:00:00 的时间戳
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long todayMidnight = cal.getTimeInMillis();

        // 昨天 00:00:00 的时间戳
        long yesterdayMidnight = todayMidnight - DAY_MILLIS;

        // 1. 今天
        if (time >= todayMidnight) {
            long diff = now - time;
            if (diff < MINUTE_MILLIS) {
                return "刚刚";
            } else if (diff < 2 * MINUTE_MILLIS) {
                return "1分钟前";
            } else if (diff < 50 * MINUTE_MILLIS) {
                return (diff / MINUTE_MILLIS) + "分钟前";
            } else if (diff < 90 * MINUTE_MILLIS) {
                return "1小时前";
            } else {
                return (diff / HOUR_MILLIS) + "小时前";
            }
        }
        // 2. 昨天
        else if (time >= yesterdayMidnight) {
            return "昨天";
        }
        // 3. 更早的时间
        else {
            return formatAbsoluteDate(time, now);
        }
    }

    /**
     * 格式化绝对日期：同一年显示 "MM-dd"，跨年份显示 "yyyy-MM-dd"
     */
    private static String formatAbsoluteDate(long time, long now) {
        Calendar targetCal = Calendar.getInstance();
        targetCal.setTimeInMillis(time);

        Calendar nowCal = Calendar.getInstance();
        nowCal.setTimeInMillis(now);

        int targetYear = targetCal.get(Calendar.YEAR);
        int currentYear = nowCal.get(Calendar.YEAR);

        if (targetYear == currentYear) {
            return new SimpleDateFormat("MM-dd", Locale.getDefault()).format(new Date(time));
        } else {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(time));
        }
    }

    /**
     * 安全补零格式化
     */
    public static String formateStr(String str) {
        try {
            int val = Integer.parseInt(str);
            return String.format(Locale.getDefault(), "%02d", val);
        } catch (Exception e) {
            return str != null ? str : "00";
        }
    }

    public static String formateInt(int val) {
        return String.format(Locale.getDefault(), "%02d", val);
    }

    /**
     * 转换为年月日格式（自动识别 10 位秒戳或 13 位毫秒戳）
     */
    public static String stampToDate(long s) {
        long millis = (s < 100000000000L) ? s * 1000 : s;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    /**
     * 转换为年月日时分秒格式（自动识别 10 位秒戳或 13 位毫秒戳）
     */
    public static String stampToDateAndTime(long s) {
        long millis = (s < 100000000000L) ? s * 1000 : s;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(millis));
    }
}