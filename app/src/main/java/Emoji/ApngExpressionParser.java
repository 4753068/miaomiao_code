package Emoji;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.LruCache;
import android.widget.TextView;

import com.github.penfeizhou.animation.apng.APNGDrawable;
import com.github.penfeizhou.animation.loader.AssetStreamLoader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 【修复点】使用当前包下的 ApngEmojiSpan
import Emoji.ApngEmojiSpan;

public class ApngExpressionParser {

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\[[^\\[\\]]+\\]");
    public static final Map<String, String> EMOJI_MAP = new LinkedHashMap<>();
    private static final LruCache<String, Drawable.ConstantState> sDrawableCache = new LruCache<>(50);

    static {
        EMOJI_MAP.put("[微笑]", "emojis/emjo_weixiao.png");
        EMOJI_MAP.put("[笑眯眯]", "emojis/emjo_xiaomimi.png");
        EMOJI_MAP.put("[憨笑]", "emojis/emjo_hanxiao.png");
        EMOJI_MAP.put("[大笑]", "emojis/emjo_daxiao.png");
        EMOJI_MAP.put("[坏笑]", "emojis/emjo_huaixiao.png");
        EMOJI_MAP.put("[阴险]", "emojis/emjo_yinxian.png");
        EMOJI_MAP.put("[眨眼]", "emojis/emjo_zhayan.png");
        EMOJI_MAP.put("[喜欢]", "emojis/emjo_xihuan.png");
        EMOJI_MAP.put("[期待]", "emojis/emjo_qidai.png");
        EMOJI_MAP.put("[卖萌]", "emojis/emjo_maimeng.png");
        EMOJI_MAP.put("[耶]", "emojis/emjo_yeah.png");
        EMOJI_MAP.put("[贴贴]", "emojis/emjo_tietie.png");
        EMOJI_MAP.put("[拜谢]", "emojis/emjo_baixie.png");
        EMOJI_MAP.put("[得意]", "emojis/emjo_deyi.png");
        EMOJI_MAP.put("[敬礼]", "emojis/emjo_jingli.png");
        EMOJI_MAP.put("[拜托]", "emojis/emjo_baituo.png");
        EMOJI_MAP.put("[吃瓜]", "emojis/emjo_chigua.png");
        EMOJI_MAP.put("[顶呱呱]", "emojis/emjo_dingguagua.png");
        EMOJI_MAP.put("[舔]", "emojis/emjo_tian.png");
        EMOJI_MAP.put("[偷笑]", "emojis/emjo_touxiao.png");
        EMOJI_MAP.put("[调皮]", "emojis/emjo_tiaopi.png");
        EMOJI_MAP.put("[捂脸]", "emojis/emjo_wulian.png");
        EMOJI_MAP.put("[嫌弃]", "emojis/emjo_xianqi.png");
        EMOJI_MAP.put("[问号脸]", "emojis/emjo_wenhaolian.png");
        EMOJI_MAP.put("[疑问]", "emojis/emjo_yiwen.png");
        EMOJI_MAP.put("[尴尬]", "emojis/emjo_ganga.png");
        EMOJI_MAP.put("[惊恐]", "emojis/emjo_jingkong.png");
        EMOJI_MAP.put("[惊讶]", "emojis/emjo_jingya.png");
        EMOJI_MAP.put("[可怜]", "emojis/emjo_kelian.png");
        EMOJI_MAP.put("[笑哭]", "emojis/emjo_xiaoku.png");
        EMOJI_MAP.put("[泪奔]", "emojis/emjo_leiben.png");
        EMOJI_MAP.put("[冷汗]", "emojis/emjo_lenghan.png");
        EMOJI_MAP.put("[流汗]", "emojis/emjo_liuhan.png");
        EMOJI_MAP.put("[难过]", "emojis/emjo_nanguo.png");
        EMOJI_MAP.put("[大哭]", "emojis/emjo_daku.png");
        EMOJI_MAP.put("[脑壳痛]", "emojis/emjo_naoketeng.png");
        EMOJI_MAP.put("[抠鼻]", "emojis/emjo_koubi.png");
        EMOJI_MAP.put("[打哈欠]", "emojis/emjo_haqian.png");
        EMOJI_MAP.put("[我牛]", "emojis/emjo_niu.png");
        EMOJI_MAP.put("[吐血]", "emojis/emjo_tuxue.png");
        EMOJI_MAP.put("[敲打]", "emojis/emjo_qiaoda.png");
        EMOJI_MAP.put("[左亲亲]", "emojis/emjo_qinqin.png");
        EMOJI_MAP.put("[右亲亲]", "emojis/emjo_youqinqin.png");
        EMOJI_MAP.put("[送花]", "emojis/emjo_songhua.png");
        EMOJI_MAP.put("[生病]", "emojis/emjo_shengbing.png");
        EMOJI_MAP.put("[呕吐]", "emojis/emjo_outu.png");
        EMOJI_MAP.put("[睡觉]", "emojis/emjo_shuijiao.png");
        EMOJI_MAP.put("[思考]", "emojis/emjo_sikao.png");
        EMOJI_MAP.put("[偷看]", "emojis/emjo_toukan.png");
        EMOJI_MAP.put("[看仔细]", "emojis/emjo_kanzixi.png");
        EMOJI_MAP.put("[黑脸]", "emojis/emjo_sui.png");
        EMOJI_MAP.put("[委屈]", "emojis/emjo_weiqu.png");
        EMOJI_MAP.put("[发呆]", "emojis/emjo_fadai.png");
        EMOJI_MAP.put("[奋斗]", "emojis/emjo_fendou.png");
        EMOJI_MAP.put("[无奈]", "emojis/emjo_wunai.png");
        EMOJI_MAP.put("[吓到]", "emojis/emjo_xia.png");
        EMOJI_MAP.put("[嘘]", "emojis/emjo_xu.png");
        EMOJI_MAP.put("[左哼哼]", "emojis/emjo_zuohengheng.png");
        EMOJI_MAP.put("[右哼哼]", "emojis/emjo_youhengheng.png");
        EMOJI_MAP.put("[晕]", "emojis/emjo_yun.png");
        EMOJI_MAP.put("[再见]", "emojis/emjo_zaijian.png");
        EMOJI_MAP.put("[咒骂]", "emojis/emjo_zhouma.png");
        EMOJI_MAP.put("[抓狂]", "emojis/emjo_zhuakuang.png");
        EMOJI_MAP.put("[发怒]", "emojis/emjo_fanu.png");
        EMOJI_MAP.put("[勾引]", "emojis/emjo_gouying.png");
        EMOJI_MAP.put("[傲慢]", "emojis/emjo_aoman.png");
        EMOJI_MAP.put("[鄙视]", "emojis/emjo_bishi.png");
        EMOJI_MAP.put("[闭嘴]", "emojis/emjo_bizui.png");
        EMOJI_MAP.put("[鼓掌]", "emojis/emjo_guzhang.png");
        EMOJI_MAP.put("[爱心]", "emojis/emjo_aixin.png");
        EMOJI_MAP.put("[心碎]", "emojis/emjo_xinsui.png");
        EMOJI_MAP.put("[拥抱]", "emojis/emjo_baobao.png");
        EMOJI_MAP.put("[抱拳]", "emojis/emjo_baoquan.png");
        EMOJI_MAP.put("[OK]", "emojis/emjo_ok.png");
        EMOJI_MAP.put("[NO]", "emojis/emjo_no.png");
        EMOJI_MAP.put("[指上]", "emojis/emjo_zhishang.png");
        EMOJI_MAP.put("[指左]", "emojis/emjo_zhizuo.png");
        EMOJI_MAP.put("[指右]", "emojis/emjo_zhiyou.png");
        EMOJI_MAP.put("[指下]", "emojis/emjo_zhixia.png");
        EMOJI_MAP.put("[握手]", "emojis/emjo_woshou.png");
        EMOJI_MAP.put("[赞]", "emojis/emjo_zan.png");
        EMOJI_MAP.put("[踩]", "emojis/emjo_bian.png");
        EMOJI_MAP.put("[比基尼]", "emojis/emjo_yongyi.png");
        EMOJI_MAP.put("[便便]", "emojis/emjo_bianbian.png");
        EMOJI_MAP.put("[药丸]", "emojis/emjo_chiyao.png");
        EMOJI_MAP.put("[虫子]", "emojis/emjo_chong.png");
        EMOJI_MAP.put("[床]", "emojis/emjo_chuang.png");
        EMOJI_MAP.put("[打电话]", "emojis/emjo_dianhua.png");
        EMOJI_MAP.put("[庆祝]", "emojis/emjo_hecai.png");
        EMOJI_MAP.put("[红唇]", "emojis/emjo_hongchun.png");
        EMOJI_MAP.put("[花]", "emojis/emjo_meigui.png");
        EMOJI_MAP.put("[枯萎]", "emojis/emjo_huaimeigui.png");
        EMOJI_MAP.put("[黄瓜]", "emojis/emjo_huanggua.png");
        EMOJI_MAP.put("[火腿]", "emojis/emjo_huotui.png");
        EMOJI_MAP.put("[啤酒]", "emojis/emjo_jiu.png");
        EMOJI_MAP.put("[咖啡]", "emojis/emjo_kafei.png");
        EMOJI_MAP.put("[酒店]", "emojis/emjo_jiudian.png");
        EMOJI_MAP.put("[开车]", "emojis/emjo_kaiche.png");
        EMOJI_MAP.put("[礼物]", "emojis/emjo_liwu.png");
        EMOJI_MAP.put("[面]", "emojis/emjo_mian.png");
        EMOJI_MAP.put("[男人]", "emojis/emjo_nanren.png");
        EMOJI_MAP.put("[女人]", "emojis/emjo_nvren.png");
        EMOJI_MAP.put("[跑步]", "emojis/emjo_paobu.png");
        EMOJI_MAP.put("[喷水]", "emojis/emjo_penshui.png");
        EMOJI_MAP.put("[葡萄]", "emojis/emjo_putao.png");
        EMOJI_MAP.put("[肌肉]", "emojis/emjo_qiangzhuang.png");
        EMOJI_MAP.put("[祈祷]", "emojis/emjo_qidao.png");
        EMOJI_MAP.put("[拳头]", "emojis/emjo_quantou.png");
        EMOJI_MAP.put("[舌头]", "emojis/emjo_shetou.png");
        EMOJI_MAP.put("[香蕉]", "emojis/emjo_xiangjiao.png");
    }

    public static SpannableString parseExpression(Context context, TextView textView, String text, int emotionSize) {
        if (text == null || text.isEmpty()) {
            return new SpannableString("");
        }

        SpannableString spannableString = new SpannableString(text);
        Matcher matcher = EXPRESSION_PATTERN.matcher(spannableString);
        boolean hasEmoji = false;

        while (matcher.find()) {
            String key = matcher.group();
            int start = matcher.start();
            int end = matcher.end();

            String assetPath = EMOJI_MAP.get(key);
            if (assetPath != null) {
                hasEmoji = true;
                APNGDrawable apngDrawable = createApngDrawable(context, assetPath, emotionSize);
                if (apngDrawable != null) {
                    ApngEmojiSpan span = new ApngEmojiSpan(apngDrawable, textView);
                    spannableString.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        // 【修复点】注释掉这里的 startAnimators！
        // 因为此时 spannableString 还没有插入到 EditText 中。
        // 如果现在启动，第一帧回调发生时 Span 还不在界面上，会导致偶尔“空白”。
        // if (hasEmoji && textView.isAttachedToWindow()) {
        //     startAnimators(spannableString);
        // }

        return spannableString;
    }

    private static APNGDrawable createApngDrawable(Context context, String assetPath, int size) {
        try {
            AssetStreamLoader loader = new AssetStreamLoader(context.getApplicationContext(), assetPath);
            APNGDrawable apngDrawable = new APNGDrawable(loader);
            if (apngDrawable != null) {
                apngDrawable.setBounds(0, 0, size, size);
                apngDrawable.setLoopLimit(0);
            }
            return apngDrawable;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void startAnimators(CharSequence text) {
        if (text instanceof Spanned) {
            Spanned spanned = (Spanned) text;
            ApngEmojiSpan[] spans = spanned.getSpans(0, spanned.length(), ApngEmojiSpan.class);
            for (ApngEmojiSpan span : spans) {
                Drawable d = span.getDrawable();
                if (d instanceof APNGDrawable) {
                    if (!((APNGDrawable) d).isRunning()) {
                        ((APNGDrawable) d).start();
                    }
                }
            }
        }
    }

    public static void stopAnimators(CharSequence text) {
        if (text instanceof Spanned) {
            Spanned spanned = (Spanned) text;
            ApngEmojiSpan[] spans = spanned.getSpans(0, spanned.length(), ApngEmojiSpan.class);
            for (ApngEmojiSpan span : spans) {
                Drawable d = span.getDrawable();
                if (d instanceof APNGDrawable) {
                    if (((APNGDrawable) d).isRunning()) {
                        ((APNGDrawable) d).stop();
                    }
                }
            }
        }
    }
}