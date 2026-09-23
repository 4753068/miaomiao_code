package adapter;

public class Module_server {
    private String server_name, server_content, server_url, socket_url;
    private int id, server_port;
    // 【新增】uid 和 type 字段
    private int uid, type;
    private boolean isOnline = true; // 新增在线状态，默认认为在线

    // 【修改】构造函数，增加 uid 和 type 参数
    public Module_server(int id, String server_name, String server_content, String socket_url, int server_port, int uid, int type) {
        this.id = id;
        this.server_name = server_name;
        this.server_content = server_content;
        this.socket_url = socket_url;
        this.server_port = server_port;
        this.uid = uid;
        this.type = type;
    }

    public int getId() { return id; }
    public String getServer_name() { return server_name; }
    public String getServer_content() { return server_content; }
    public String getSocket_url() { return socket_url; }
    public int getServer_port() { return server_port; }

    // 【新增】uid 和 type 的 Getter 和 Setter
    public int getUid() { return uid; }
    public void setUid(int uid) { this.uid = uid; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    // 新增：在线状态的 Getter 和 Setter
    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }
}