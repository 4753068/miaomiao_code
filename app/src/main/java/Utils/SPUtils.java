package Utils; // 请根据你的项目结构修改包名

import android.content.Context;
import android.content.SharedPreferences;

import com.qapp.midian.App;

import java.util.Map;
import org.json.JSONObject;
import java.util.Map;
public class SPUtils {

    // 默认的 SharedPreferences 文件名
    private static final String DEFAULT_SP_NAME = "app_config";
    private static SPUtils instance;
    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    // 私有构造函数，防止外部直接 new
    private SPUtils(Context context, String spName) {
        // 使用 ApplicationContext 防止内存泄漏
        sharedPreferences = context.getApplicationContext().getSharedPreferences(spName, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    /**
     * 初始化单例（建议在 Application 的 onCreate 中调用）
     *
     * @param context 上下文
     */
    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new SPUtils(context, DEFAULT_SP_NAME);
        }
    }

    /**
     * 如果需要自定义 SP 文件名，可以使用这个初始化方法
     */
    public static synchronized void init(Context context, String spName) {
        if (instance == null) {
            instance = new SPUtils(context, spName);
        }
    }

    /**
     * 获取单例实例
     */
    public static SPUtils getInstance() {
        if (instance == null) {
            throw new NullPointerException("SPUtils is not initialized. Please call init(Context) first, preferably in your Application class.");
        }
        return instance;
    }


    /**
     * 保存数据（自动识别类型）
     *
     * @param key   键
     * @param value 值（支持 String, Integer, Boolean, Float, Long）
     */
    public void put(String key, Object value) {
        if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else {
            if (value != null) {
                editor.putString(key, value.toString());
            } else {
                editor.putString(key, null);
            }
        }
        // 使用 apply 异步提交，性能更好
        editor.apply();
    }

    /**
     * 获取数据（自动识别并转换类型）
     *
     * @param key          键
     * @param defaultValue 默认值（根据默认值的类型来决定返回的数据类型）
     * @return 返回对应类型的值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        if (defaultValue instanceof String) {
            return (T) sharedPreferences.getString(key, (String) defaultValue);
        } else if (defaultValue instanceof Integer) {
            return (T) Integer.valueOf(sharedPreferences.getInt(key, (Integer) defaultValue));
        } else if (defaultValue instanceof Boolean) {
            return (T) Boolean.valueOf(sharedPreferences.getBoolean(key, (Boolean) defaultValue));
        } else if (defaultValue instanceof Float) {
            return (T) Float.valueOf(sharedPreferences.getFloat(key, (Float) defaultValue));
        } else if (defaultValue instanceof Long) {
            return (T) Long.valueOf(sharedPreferences.getLong(key, (Long) defaultValue));
        }
        return defaultValue;
    }


    /**
     * 移除某个key对应的值
     */
    public void remove(String key) {
        editor.remove(key).apply();
    }

    /**
     * 清除所有数据
     */
    public void clear() {
        editor.clear().apply();
    }

    /**
     * 查询某个key是否已经存在
     */
    public boolean contains(String key) {
        return sharedPreferences.contains(key);
    }

    /**
     * 返回所有的键值对
     */
    public Map<String, ?> getAll() {
        return sharedPreferences.getAll();
    }

    // 在 SPUtils 类中添加此方法.获取app_config中的所有值
    public String getAllAsJson() {
        Map<String, ?> allEntries = sharedPreferences.getAll();
        JSONObject jsonObject = new JSONObject();
        try {
            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                jsonObject.put(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonObject.toString();
    }
}