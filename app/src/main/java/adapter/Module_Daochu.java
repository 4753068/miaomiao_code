package adapter;

public class Module_Daochu {
    private String filename;
    private String path;
    private int uid;

    public Module_Daochu(String filename, String path,int uid) {
        this.filename=filename;
        this.path=path;
        this.uid=uid;
    }

    public String getFilename() {
        return filename;
    }
    public String getPath() {
        return path;
    }
    public int getUid() {
        return uid;
    }
}
