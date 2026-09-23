package com.qapp.midian;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.github.chrisbanes.photoview.PhotoView;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class MediaPagerAdapter extends RecyclerView.Adapter<MediaPagerAdapter.ViewHolder> {

    private final Context context;
    private final List<MediaItemData> mediaList;

    private ExoPlayer exoPlayer;
    private int currentPlayingPosition = -1;
    private ViewHolder currentVideoHolder;

    // 💡 用于自主控制进度条同步与自动隐藏的 Handler 链
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    private Runnable hideControllerRunnable;
    private boolean isUserTrackingSeekBar = false;

    public static class MediaItemData {
        public String mediaUrl;
        public String type;

        public MediaItemData(String mediaUrl, String type) {
            this.mediaUrl = mediaUrl;
            this.type = type;
        }
    }

    public MediaPagerAdapter(Context context, List<MediaItemData> mediaList) {
        this.context = context;
        this.mediaList = mediaList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_media_pager, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MediaItemData item = mediaList.get(position);
        String realPath = getRealFilePath(item.mediaUrl);
        File file = new File(realPath);

        holder.photoView.setVisibility(View.GONE);
        holder.videoContainer.setVisibility(View.GONE);
        holder.photoView.setImageBitmap(null);
        holder.playerView.setPlayer(null);
        holder.customController.setVisibility(View.GONE);
        holder.customSeekBar.setProgress(0);

        if (!file.exists()) {
            Log.e("MediaAdapter", "文件不存在: " + realPath);
            return;
        }

        String extension = MimeTypeMap.getFileExtensionFromUrl(realPath);
        if (extension == null || extension.isEmpty()) {
            int lastDot = realPath.lastIndexOf('.');
            if (lastDot >= 0) {
                extension = realPath.substring(lastDot + 1);
            }
        }
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());

        if ((mimeType != null && (mimeType.startsWith("video/") || mimeType.contains("webm")))
                || realPath.toLowerCase().endsWith(".webm")) {
            item.type = "VIDEO";
            holder.videoContainer.setVisibility(View.VISIBLE);
        } else {
            holder.photoView.setVisibility(View.VISIBLE);

            if (realPath.toLowerCase().endsWith(".gif") || realPath.toLowerCase().endsWith(".webp")
                    || (mimeType != null && (mimeType.contains("gif") || mimeType.contains("webp")))) {
                item.type = "GIF";
                Glide.with(context)
                        .asGif()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .load(file)
                        .into(holder.photoView);
            } else {
                item.type = "IMAGE";
                Glide.with(context)
                        .asBitmap()
                        .load(file)
                        .into(holder.photoView);
            }

            holder.photoView.setOnMatrixChangeListener(rect -> {
                float currentScale = holder.photoView.getScale();
                View parent = (View) holder.photoView.getParent();
                while (parent != null && !(parent instanceof androidx.viewpager2.widget.ViewPager2)) {
                    parent = (View) parent.getParent();
                }
                if (parent instanceof androidx.viewpager2.widget.ViewPager2) {
                    ((androidx.viewpager2.widget.ViewPager2) parent).setUserInputEnabled(currentScale <= 1.05f);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return mediaList != null ? mediaList.size() : 0;
    }

    /**
     * 播放当前视频（自主重构版：绝对无闪现，点击100%呼出控制条）
     */
    public void playVideoAtPosition(int position, ViewHolder holder) {
        stopVideo();

        if (position < 0 || position >= mediaList.size()) return;
        MediaItemData item = mediaList.get(position);
        if (!"VIDEO".equals(item.type)) return;

        currentPlayingPosition = position;
        currentVideoHolder = holder;
        String realPath = getRealFilePath(item.mediaUrl);

        try {
            holder.videoContainer.setVisibility(View.VISIBLE);
            holder.photoView.setVisibility(View.GONE);
            holder.customController.setVisibility(View.GONE); // 确保初始时完全摸黑全隐

            // 1. 初始化引擎并绑定
            exoPlayer = new ExoPlayer.Builder(context).build();
            holder.playerView.setPlayer(exoPlayer);

            // 🌟 2. 默认静音控制与事件绑定
            exoPlayer.setVolume(0f); // 初始化强制设为静音 (0f)
            final boolean[] isMuted = {true}; // 状态标记：默认处于静音状态
            // （如果您项目中有自己好看的静音图标，请把下方 android.R.drawable... 换成 R.drawable.您的图标）
            holder.btnMuteToggle.setImageResource(R.drawable.jingyin);

            holder.btnMuteToggle.setOnClickListener(v -> {
                isMuted[0] = !isMuted[0];
                if (isMuted[0]) {
                    exoPlayer.setVolume(0f);
                    holder.btnMuteToggle.setImageResource(R.drawable.jingyin); // 设置为"已静音"状态图标
                } else {
                    exoPlayer.setVolume(1f);
                    holder.btnMuteToggle.setImageResource(R.drawable.yousheng); // 设置为"有声音"状态图标
                }
                resetAutoHideTimer(); // 防止点击按钮时控制条突然消失，重置3秒倒计时
            });

            // 3. 加载源
            MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(new File(realPath)));
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);

            // 4. 监听就绪状态，初始化进度条最大值与时间
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY && exoPlayer != null) {
                        long duration = exoPlayer.getDuration();
                        holder.customSeekBar.setMax((int) duration);
                        holder.txtDuration.setText(formatTime((int) duration));
                        startProgressUpdater(); // 启动时间步进器
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        stopProgressUpdater();
                        holder.customController.setVisibility(View.VISIBLE);
                        holder.customSeekBar.setProgress(holder.customSeekBar.getMax());
                    }
                }
            });

            // 5. 进度条拖拽联动响应
            holder.customSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        holder.txtCurrentTime.setText(formatTime(progress));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    isUserTrackingSeekBar = true;
                    handler.removeCallbacks(hideControllerRunnable); // 拖动时不允许自动隐藏
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (exoPlayer != null) {
                        exoPlayer.seekTo(seekBar.getProgress());
                    }
                    isUserTrackingSeekBar = false;
                    resetAutoHideTimer(); // 放手后重新计算3秒隐藏
                }
            });

            // 6. 💡 重构核心手势：点击全屏画面切换控制条显隐与播放/暂停
            View.OnClickListener customTapListener = v -> {
                if (exoPlayer != null) {
                    // 如果控制条是隐藏的 -> 点击它只负责把控制条叫醒出来，不中断视频
                    if (holder.customController.getVisibility() == View.GONE) {
                        holder.customController.setVisibility(View.VISIBLE);
                        resetAutoHideTimer(); // 启动3秒自动倒计时隐藏
                    } else {
                        // 如果控制条已经是显示的 -> 点击它负责切换 播放/暂停
                        if (exoPlayer.isPlaying()) {
                            exoPlayer.pause();
                            handler.removeCallbacks(hideControllerRunnable); // 暂停期间控制条常驻，不自动隐藏
                        } else {
                            exoPlayer.play();
                            resetAutoHideTimer(); // 恢复播放后重新倒计时
                        }
                    }
                }
            };

            // 分别向两层容器注册，彻底解决原生系统手势被吞噬的问题
            holder.playerView.setOnClickListener(customTapListener);
            if (holder.playerView.getVideoSurfaceView() != null) {
                holder.playerView.getVideoSurfaceView().setOnClickListener(customTapListener);
            }

            // 7. 启航播放
            exoPlayer.prepare();
            exoPlayer.play();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 独立线程：每 150 毫秒刷新一次自定义进度条
     */
    private void startProgressUpdater() {
        stopProgressUpdater();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null && exoPlayer.isPlaying() && !isUserTrackingSeekBar && currentVideoHolder != null) {
                    int currentPos = (int) exoPlayer.getCurrentPosition();
                    currentVideoHolder.customSeekBar.setProgress(currentPos);
                    currentVideoHolder.txtCurrentTime.setText(formatTime(currentPos));
                }
                handler.postDelayed(this, 150);
            }
        };
        handler.post(progressRunnable);
    }

    private void stopProgressUpdater() {
        if (progressRunnable != null) {
            handler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }

    /**
     * 独立线程：无操作 3 秒后自动关闭控制条
     */
    private void resetAutoHideTimer() {
        if (hideControllerRunnable != null) {
            handler.removeCallbacks(hideControllerRunnable);
        }
        hideControllerRunnable = () -> {
            if (currentVideoHolder != null && exoPlayer != null && exoPlayer.isPlaying()) {
                currentVideoHolder.customController.setVisibility(View.GONE);
            }
        };
        handler.postDelayed(hideControllerRunnable, 3000);
    }

    private String formatTime(int milliseconds) {
        int totalSeconds = milliseconds / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private String getRealFilePath(String originalPath) {
        if (originalPath == null) return "";
        originalPath = originalPath.trim();
        if (originalPath.endsWith(".enc")) {
            String baseUrl = originalPath.substring(0, originalPath.lastIndexOf(".enc"));
            String[] extensions = {".mp4", ".webm", ".jpg", ".jpeg", ".png", ".gif", ".webp"};
            for (String ext : extensions) {
                File file = new File(baseUrl + ext);
                if (file.exists()) return baseUrl + ext;
            }
            return baseUrl + ".mp4";
        }
        return originalPath;
    }

    public void stopVideo() {
        stopProgressUpdater();
        if (hideControllerRunnable != null) {
            handler.removeCallbacks(hideControllerRunnable);
        }
        if (exoPlayer != null) {
            try {
                exoPlayer.stop();
                exoPlayer.release();
            } catch (Exception e) { e.printStackTrace(); }
            exoPlayer = null;
        }

        if (currentVideoHolder != null) {
            currentVideoHolder.videoContainer.setVisibility(View.GONE);
            currentVideoHolder.customController.setVisibility(View.GONE);
            currentVideoHolder.playerView.setPlayer(null);
            currentVideoHolder = null;
        }
        currentPlayingPosition = -1;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public PhotoView photoView;
        public RelativeLayout videoContainer;
        public PlayerView playerView;

        // 自定义绑定组件
        public LinearLayout customController;
        public SeekBar customSeekBar;
        public TextView txtCurrentTime;
        public TextView txtDuration;
        public ImageView btnMuteToggle; // 🌟 绑定新控件

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            photoView = itemView.findViewById(R.id.itemPhotoView);
            videoContainer = itemView.findViewById(R.id.itemVideoContainer);
            playerView = itemView.findViewById(R.id.itemExoPlayerView);

            customController = itemView.findViewById(R.id.customVideoController);
            customSeekBar = itemView.findViewById(R.id.customSeekBar);
            txtCurrentTime = itemView.findViewById(R.id.customCurrentTime);
            txtDuration = itemView.findViewById(R.id.customDuration);
            btnMuteToggle = itemView.findViewById(R.id.btnMuteToggle); // 🌟 绑定新控件
        }
    }
}