package adapter;

public class Module_Chat {
    private int _ID;
    private String _Message;
    public String _MessageId;
    private int _UID;
    private int _FromUID;
    private String _Thumb;
    private String _Time;
    private String _Yingyong;
    private String _Mediaurl;
    private int _IsRead;
    public int _Progress;

    // ======== 用于维护沉浸式下载状态的字段 ========
    public boolean isDownloading = false;
    public boolean isDownloaded = false;
    public String localFilePath = "";
    // ===============================================

    public Module_Chat(int _ID, String _Message, String _MessageId, int UID, int _FromUID, String _Thumb, String _Time, String _Yingyong, String _Mediaurl, int _IsRead, int _Progress) {
        this._ID = _ID;
        this._Message = _Message;
        this._MessageId = _MessageId;
        this._UID = UID;
        this._FromUID = _FromUID;
        this._Thumb = _Thumb;
        this._Time = _Time;
        this._Yingyong = _Yingyong;
        this._Mediaurl = _Mediaurl;
        this._IsRead = _IsRead;
        this._Progress = _Progress;
    }

    public int getID() {
        return _ID;
    }

    public void setID(int _ID) {
        this._ID = _ID;
    }

    public String getMessage() {
        return _Message;
    }

    public void setMessage(String _Message) {
        this._Message = _Message;
    }

    public int getUID() {
        return _UID;
    }

    public void setUID(int _UID) {
        this._UID = _UID;
    }

    public String getMessageId() {
        return _MessageId;
    }

    public void setMessageId(String _MessageId) {
        this._MessageId = _MessageId;
    }

    public int getFromUID() {
        return _FromUID;
    }

    public void setFromUID(int _FromUID) {
        this._FromUID = _FromUID;
    }

    public String getThumb() {
        return _Thumb;
    }

    public void setThumb(String _Thumb) {
        this._Thumb = _Thumb;
    }

    public String getTime() {
        return _Time;
    }

    public void setTime(String _Time) {
        this._Time = _Time;
    }

    public String getYingyong() {
        return _Yingyong;
    }

    public void setYingyong(String _Yingyong) {
        this._Yingyong = _Yingyong;
    }

    public String getMediaurl() {
        return _Mediaurl;
    }

    public void setMediaurl(String _Mediaurl) {
        this._Mediaurl = _Mediaurl;
    }

    public int get_IsRead() {
        return _IsRead;
    }

    public void setIsRead(int _IsRead) {
        this._IsRead = _IsRead;
    }

    public int get_Progress() {
        return _Progress;
    }

    public void set_Progress(int progress) {
        this._Progress = progress;
    }
}