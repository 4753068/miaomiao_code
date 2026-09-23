package Utils;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class FlipDetector implements SensorEventListener {

    // 定义翻转与隐私手势触发的回调接口
    public interface OnFlipListener {
        // 触发动作执行方法
        void onFlipped();
    }

    // 声明系统传感器管理器引用
    private final SensorManager sensorManager;
    // 声明重力或主加速度传感器引用
    private final Sensor gravitySensor;
    // 声明距离传感器引用
    private final Sensor proximitySensor;
    // 声明陀螺仪传感器引用
    private final Sensor gyroscopeSensor;
    // 声明光线传感器引用
    private final Sensor lightSensor;
    // 声明硬件线性加速度传感器引用
    private final Sensor linearAccelerationSensor;

    // 声明外部回调监听实例
    private final OnFlipListener listener;

    // 记录屏幕当前是否严格水平朝下
    private boolean isFaceDown = false;
    // 记录距离传感器是否处于贴近状态
    private boolean isNear = false;
    // 记录最近是否发生过快速翻转动作的时间戳
    private long lastRotateTime = 0L;
    // 记录当前是否处于光线遮挡状态
    private boolean isDarkCovered = false;

    // 用于无硬件线性加速度时的软件重力滤波缓存数组（X, Y, Z）
    private final float[] gravityValues = new float[3];
    // 记录上次成功触发回桌面的时间戳，用于防重复触发
    private long lastTriggerTime = 0L;
    // 记录开始监听的时间戳，用于跳过页面初始加载时的误判定
    private long startTime = 0L;

    // 功能开关：是否要求有动态旋转过程（开启可彻底防止静止侧放误触）
    private boolean enableFastFlipCheck = true;
    // 功能开关：是否启用手掌遮盖屏幕触发
    private boolean enablePalmCover = true;
    // 功能开关：是否启用紧急摇晃触发（默认开启）
    private boolean enableShakeTrigger = true;

    // 构造函数：初始化所有传感器服务与参数
    public FlipDetector(Context context, OnFlipListener listener) {
        // 绑定外部传入的回调监听接口
        this.listener = listener;
        // 获取系统传感器管理服务
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        // 尝试获取硬件重力传感器
        Sensor gSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        // 若设备无重力传感器，降级获取普通加速度传感器
        if (gSensor == null) {
            gSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        // 保存重力传感器对象
        this.gravitySensor = gSensor;

        // 获取硬件距离传感器
        this.proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        // 获取硬件陀螺仪传感器
        this.gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        // 获取硬件光线传感器
        this.lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        // 尝试获取硬件线性加速度传感器
        this.linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    // 启动所有开启功能的传感器监听
    public void start() {
        // 记录启动时刻的时间戳，用于构建启动保护宽限期
        startTime = System.currentTimeMillis();

        // 注册重力/加速度传感器，采样频率设定为 UI 刷新级别
        if (gravitySensor != null) {
            sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_UI);
        }
        // 注册距离传感器
        if (proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI);
        }
        // 注册陀螺仪传感器
        if (gyroscopeSensor != null) {
            sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_UI);
        }
        // 若允许手掌遮盖且设备具备光线传感器，注册光线传感器
        if (lightSensor != null && enablePalmCover) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
        }
        // 若硬件支持线性加速度且开启晃动检测，注册硬件线性加速度监听
        if (linearAccelerationSensor != null && enableShakeTrigger) {
            sensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    // 停止传感器监听，彻底释放硬件占用与避免电池消耗
    public void stop() {
        // 注销本类监听的所有系统传感器
        sensorManager.unregisterListener(this);
        // 重置屏幕朝下状态标志位
        isFaceDown = false;
        // 重置距离贴近标志位
        isNear = false;
        // 重置旋转历史时间戳
        lastRotateTime = 0L;
        // 重置光线暗度标志位
        isDarkCovered = false;
        // 清理软件重力滤波缓存
        gravityValues[0] = 0.0f;
        gravityValues[1] = 0.0f;
        gravityValues[2] = 0.0f;
    }

    // 传感器数值更新总回调入口
    @Override
    public void onSensorChanged(SensorEvent event) {
        // 获取当前事件触发的系统毫秒级时间戳
        long now = System.currentTimeMillis();

        // 刚进入界面的前 600ms 内只更新传感器状态，不触发任何退出动作，防止进页面秒退
        if (now - startTime < 600) {
            return;
        }

        // 处理重力或普通加速度数据
        if (event.sensor.getType() == Sensor.TYPE_GRAVITY || event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // 获取 X 轴读数
            float x = event.values[0];
            // 获取 Y 轴读数
            float y = event.values[1];
            // 获取 Z 轴读数
            float z = event.values[2];

            // 判断 Z 轴是否显著朝下（平扣在桌上时接近 -9.8）
            boolean isZDown = (z < -8.0f);
            // 严格检查水平面倾角：X 轴与 Y 轴的绝对分量均需低于 4.0，排除倾斜、斜靠、侧放的误触
            boolean isHorizontallyFlat = (Math.abs(x) < 4.0f && Math.abs(y) < 4.0f);

            // 只有当机身接近水平平扣时才判定为朝下
            isFaceDown = isZDown && isHorizontallyFlat;

            // 兼容性保障：若机型没有硬件线性加速度传感器，通过低通滤波算法软件提取加速度
            if (linearAccelerationSensor == null && enableShakeTrigger) {
                // 滤波系数 alpha
                final float alpha = 0.8f;
                // 计算重力低通分量
                gravityValues[0] = alpha * gravityValues[0] + (1 - alpha) * x;
                gravityValues[1] = alpha * gravityValues[1] + (1 - alpha) * y;
                gravityValues[2] = alpha * gravityValues[2] + (1 - alpha) * z;

                // 原始值减去重力分量即为线性净加速度
                float linearX = x - gravityValues[0];
                float linearY = y - gravityValues[1];
                float linearZ = z - gravityValues[2];

                // 计算三维合成加速度模长
                double netAcc = Math.sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ);
                // 净加速度大于 13.0 判定为受到用力甩动或剧烈晃动
                if (netAcc > 13.0) {
                    dispatchTrigger(now);
                    return;
                }
            }
        }

        // 处理距离传感器数据
        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            // 获取物距数值
            float distance = event.values[0];
            // 获取最大探测量程，默认回退值为 5cm
            float maxRange = (proximitySensor != null) ? proximitySensor.getMaximumRange() : 5.0f;
            // 贴近阈值设定为最大量程与 2.0cm 的较小者
            float threshold = Math.min(maxRange, 2.0f);
            // 判定是否紧贴物体表面
            isNear = (distance < threshold);
        }

        // 处理陀螺仪旋转数据
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            // 获取三轴角速度读数
            float wx = event.values[0];
            float wy = event.values[1];
            float wz = event.values[2];
            // 计算合成翻转角速度模长
            double speed = Math.sqrt(wx * wx + wy * wy + wz * wz);
            // 角速度大于 1.8 rad/s 标记当前或刚才发生过快速旋转
            if (speed > 1.8) {
                lastRotateTime = now;
            }
        }

        // 处理光线传感器读数
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            // 获取光照流明值
            float lux = event.values[0];
            // 流明低于 2.0 Lux 判定为完全被遮挡
            isDarkCovered = (lux < 2.0f);
        }

        // 处理具备硬件线性加速度传感器的设备摇晃事件
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION && enableShakeTrigger) {
            // 读取三轴线性加速度分量
            float ax = event.values[0];
            float ay = event.values[1];
            float az = event.values[2];
            // 计算三维净加速度模长
            double netAcceleration = Math.sqrt(ax * ax + ay * ay + az * az);
            // 加速度突破阈值（13.0 m/s²）判定为紧急晃动
            if (netAcceleration > 13.0) {
                dispatchTrigger(now);
                return;
            }
        }

        // 判定最近 800ms 内是否发生过动态旋转（即使刚扣在桌上静止后依然判定通过）
        boolean hadRecentRotation = (now - lastRotateTime < 800);

        // 翻转扣桌判定基础条件：水平面平扣朝下 并且 贴近桌面
        boolean isFlipTriggered = isFaceDown && (isNear || proximitySensor == null);

        // 若启用了陀螺仪校验且设备具备陀螺仪：要求必须是旋转后扣下的，严防在侧翻放置时静态触发
        if (enableFastFlipCheck && gyroscopeSensor != null) {
            isFlipTriggered = isFlipTriggered && hadRecentRotation;
        }

        // 手掌遮盖判定条件：光照趋于零 并且 距离传感器遮挡
        boolean isPalmCoverTriggered = enablePalmCover && isDarkCovered && isNear;

        // 满足翻转或遮盖任意条件时执行分发
        if (isFlipTriggered || isPalmCoverTriggered) {
            dispatchTrigger(now);
        }
    }

    // 动作触发调度与防抖过滤
    private void dispatchTrigger(long currentTime) {
        // 距离上次触发动作需超过 1500 毫秒才允许下一次触发
        if (currentTime - lastTriggerTime > 1500) {
            // 刷新上一次触发动作的时间戳
            lastTriggerTime = currentTime;
            // 回调执行通知
            if (listener != null) {
                listener.onFlipped();
            }
        }
    }

    // 传感器精度变动回调（接口必需，留空实现）
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 空实现
    }

    // 静态辅助方法：安全将 Activity 移入后台实现返回桌面
    public static void returnToHome(Activity that) {
        // 校验界面实例是否有效且尚未被销毁
        if (that != null && !that.isFinishing()) {
            // 调用系统底层栈管理方法将任务推入后台
            that.moveTaskToBack(true);
        }
    }

    // 动态配置：设置是否开启旋转角速度校验
    public void setEnableFastFlipCheck(boolean enable) {
        this.enableFastFlipCheck = enable;
    }

    // 动态配置：设置是否开启手掌遮盖退出
    public void setEnablePalmCover(boolean enable) {
        this.enablePalmCover = enable;
    }

    // 动态配置：设置是否开启紧急摇晃退出
    public void setEnableShakeTrigger(boolean enable) {
        this.enableShakeTrigger = enable;
    }
}