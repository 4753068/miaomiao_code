package Utils;


/**
 * 聊天积分计算工具
 */
public final class ChatPointUtil {

    private ChatPointUtil() {
    }

    /**
     * 消息类型
     */
    public enum MessageType {

        /**
         * 文字
         */
        TEXT,

        /**
         * 图片
         */
        IMAGE,

        /**
         * 语音
         */
        AUDIO,

        /**
         * 视频
         */
        VIDEO,

        /**
         * 文件
         */
        FILE,

        /**
         * Emoji/表情（免费）
         */
        EMOJI
    }

    /**
     * 计算发送消息需要扣除的积分
     *
     * @param type 消息类型
     * @param content 文本内容（非文字可传null）
     * @param fileSizeBytes 文件大小(byte)，文本传0
     * @return 所需积分
     */
    public static int calculatePoints(
            MessageType type,
            String content,
            long fileSizeBytes) {

        switch (type) {

            case TEXT:
                return calculateText(content);

            case IMAGE:
                return calculateMedia(fileSizeBytes, 5, 1);

            case AUDIO:
                return calculateMedia(fileSizeBytes, 5, 1);

            case VIDEO:
                return calculateMedia(fileSizeBytes, 20, 2);

            case FILE:
                return calculateMedia(fileSizeBytes, 10, 1);

            case EMOJI:
                return 0;

            default:
                return 0;
        }
    }

    /**
     * 文本积分
     */
    private static int calculateText(String text) {

        if (text == null || text.trim().isEmpty()) {
            return 0;
        }

        // Unicode字符数量（Emoji算1个）
        int length = text.codePointCount(0, text.length());

        if (length <= 20) {
            return 1;
        }

        if (length <= 50) {
            return 2;
        }

        if (length <= 100) {
            return 3;
        }

        int extra = length - 100;

        return 3 + (extra + 49) / 50;
    }

    /**
     * 图片/语音/视频/文件积分
     *
     * @param fileSizeBytes 文件大小(Byte)
     * @param basePoint 最低积分
     * @param pointPer100KB 每100KB增加积分
     */
    private static int calculateMedia(
            long fileSizeBytes,
            int basePoint,
            int pointPer100KB) {

        if (fileSizeBytes <= 0) {
            return basePoint;
        }

        // 向上取整到KB
        long kb = (fileSizeBytes + 1023) / 1024;

        // 每100KB按一档，不足100KB按100KB计算
        long blocks = (kb + 99) / 100;

        return basePoint + (int) (blocks * pointPer100KB);
    }

}