package adapter;

public class Module_Friend {
    private int id;
    private int uid;
    private int friendUid;
    private String message;
    private String userid;
    private String nickname;
    private int type;
    private String thumb;
    private int messageNumber;
    private long updateTime;
    private String url;
    private int isTop; // ✨ 0: 未置顶, 1: 已置顶

    public Module_Friend(int uid, int friendUid, String message, String userid, String nickname, int type, String thumb, int messageNumber, long updateTime, String url, int isTop) {
        this.uid = uid;
        this.friendUid = friendUid;
        this.message = message;
        this.userid = userid;
        this.nickname = nickname;
        this.type = type;
        this.thumb = thumb;
        this.messageNumber = messageNumber;
        this.updateTime = updateTime;
        this.url = url;
        this.isTop = isTop;
    }

    // 保持兼容旧构造函数
    public Module_Friend(int uid, int friendUid, String message, String userid, String nickname, int type, String thumb, int messageNumber, long updateTime, String url) {
        this(uid, friendUid, message, userid, nickname, type, thumb, messageNumber, updateTime, url, 0);
    }

    public int getIsTop() { return isTop; }
    public void setIsTop(int isTop) { this.isTop = isTop; }

    public int getId() { return id; }
    public int getUid() { return uid; }
    public int getFriendUid() { return friendUid; }
    public String getMessage() { return message; }
    public String getUserid() { return userid; }
    public String getNickname() { return nickname; }
    public int getType() { return type; }
    public String getThumb() { return thumb; }
    public int getMessageNumber() { return messageNumber; }
    public long getUpdateTime() { return updateTime; }
    public String getUrl() { return url; }
}