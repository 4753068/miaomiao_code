package Utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.qapp.midian.App;

import java.util.concurrent.atomic.AtomicInteger;

public class Database extends SQLiteOpenHelper {
    private static final String TAG = "Database";
    private static final String DB_NAME = "qapp_midian.db";

    // ✨ 升级数据库版本到 34
    private static final int VERSION = 34;
    private final AtomicInteger mOpenCounter = new AtomicInteger();

    public Database(Context context) {
        super(context, DB_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        initTables(db);
        createIndexes(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < newVersion) {
            // 1. 处理 friend 表缺少 ImageUrl 字段
            if (isTableExists(db, "friend")) {
                if (!isColumnExists(db, "friend", "ImageUrl")) {
                    try {
                        db.execSQL("ALTER TABLE friend ADD COLUMN ImageUrl TEXT");
                    } catch (Exception e) {
                        Log.e(TAG, "添加 ImageUrl 字段失败", e);
                    }
                }
                // ✨ 2. 为 friend 表增加 IsTop 置顶字段
                if (!isColumnExists(db, "friend", "IsTop")) {
                    try {
                        db.execSQL("ALTER TABLE friend ADD COLUMN IsTop INTEGER DEFAULT 0");
                        Log.i(TAG, "成功为 friend 表添加 IsTop 字段");
                    } catch (Exception e) {
                        Log.e(TAG, "添加 IsTop 字段失败", e);
                    }
                }
                // ✨ 3. 为 friend 表增加 miandarao 免打扰字段
                if (!isColumnExists(db, "friend", "miandarao")) {
                    try {
                        db.execSQL("ALTER TABLE friend ADD COLUMN miandarao INTEGER DEFAULT 0");
                        Log.i(TAG, "成功为 friend 表添加 miandarao 字段");
                    } catch (Exception e) {
                        Log.e(TAG, "添加 miandarao 字段失败", e);
                    }
                }
            }

            // 4. 为 server 表动态增加 uid, type, password 字段
            if (isTableExists(db, "server")) {
                if (!isColumnExists(db, "server", "uid")) {
                    try {
                        db.execSQL("ALTER TABLE server ADD COLUMN uid INTEGER DEFAULT 0");
                    } catch (Exception ignored) {}
                }
                if (!isColumnExists(db, "server", "type")) {
                    try {
                        db.execSQL("ALTER TABLE server ADD COLUMN type INTEGER DEFAULT 0");
                    } catch (Exception ignored) {}
                }
                if (!isColumnExists(db, "server", "password")) {
                    try {
                        db.execSQL("ALTER TABLE server ADD COLUMN password TEXT");
                    } catch (Exception ignored) {}
                }
            } else {
                String server = "CREATE TABLE IF NOT EXISTS server(id INTEGER PRIMARY KEY AUTOINCREMENT, server_name TEXT, server_content TEXT, server_url TEXT, socket_url TEXT, server_port INTEGER, server_district TEXT, uid INTEGER DEFAULT 0, type INTEGER DEFAULT 0, password TEXT)";
                db.execSQL(server);
            }

            // 5. 为 chat 表增加 GroupId 和 ChatType 字段
            if (isTableExists(db, "chat")) {
                if (!isColumnExists(db, "chat", "GroupId")) {
                    try {
                        db.execSQL("ALTER TABLE chat ADD COLUMN GroupId TEXT");
                    } catch (Exception ignored) {}
                }
                if (!isColumnExists(db, "chat", "ChatType")) {
                    try {
                        db.execSQL("ALTER TABLE chat ADD COLUMN ChatType INTEGER DEFAULT 0");
                    } catch (Exception ignored) {}
                }
            }

            createIndexes(db);
        }
    }

    private void initTables(SQLiteDatabase db) {
        String cookies = "CREATE TABLE IF NOT EXISTS Cookies(id INTEGER PRIMARY KEY AUTOINCREMENT, cookies TEXT, url TEXT)";
        db.execSQL(cookies);

        String chat = "CREATE TABLE IF NOT EXISTS chat(id INTEGER PRIMARY KEY AUTOINCREMENT, MessageId TEXT, GroupId TEXT, ChatType INTEGER DEFAULT 0, FromUid INTEGER, ToUid INTEGER, Message TEXT, Yingyong TEXT, Inputtime INTEGER, IsRead INTEGER, Mediaurl TEXT, Msgkey TEXT)";
        db.execSQL(chat);

        // ✨ 密电好友列表：增加了 IsTop 与 miandarao 字段[cite: 11]
        String friend = "CREATE TABLE IF NOT EXISTS friend(id INTEGER PRIMARY KEY AUTOINCREMENT, UID INTEGER, Friend_UID INTEGER, UserID TEXT, Nickname TEXT, _Nickname TEXT, Sex INTEGER, Image TEXT, ImageUrl TEXT, FriendKey TEXT DEFAULT '123456789654321', MessageNumber INTEGER DEFAULT 0, Type INTEGER, UpdateTime INTEGER DEFAULT 0, Content TEXT, IsTop INTEGER DEFAULT 0, miandarao INTEGER DEFAULT 0)";
        db.execSQL(friend);

        String emClass = "CREATE TABLE IF NOT EXISTS em_class(id INTEGER PRIMARY KEY AUTOINCREMENT, EmId INTEGER, Em_Class TEXT, Thumb TEXT, Url TEXT)";
        db.execSQL(emClass);

        String em = "CREATE TABLE IF NOT EXISTS em(id INTEGER PRIMARY KEY AUTOINCREMENT, EmId INTEGER, Em_Class INTEGER, Em TEXT, Tag TEXT, Res INTEGER, UpdateTime INTEGER)";
        db.execSQL(em);

        String serviceList = "CREATE TABLE IF NOT EXISTS service(id INTEGER PRIMARY KEY AUTOINCREMENT, servicename TEXT, content TEXT, url TEXT, serviceId INTEGER, thumb TEXT, price REAL, sign TEXT, onlymp3 INTEGER)";
        db.execSQL(serviceList);

        String videoList = "CREATE TABLE IF NOT EXISTS video(id INTEGER PRIMARY KEY AUTOINCREMENT, videoId INTEGER, serviceId INTEGER, title TEXT, m3u8 TEXT, mp3 TEXT, mp4 TEXT, thumb TEXT, duration INTEGER, click INTEGER, isPlay INTEGER, price REAL)";
        db.execSQL(videoList);

        String videoAd = "CREATE TABLE IF NOT EXISTS ad(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, url TEXT, thumb TEXT, updatetime INTEGER, adid INTEGER)";
        db.execSQL(videoAd);

        String server = "CREATE TABLE IF NOT EXISTS server(id INTEGER PRIMARY KEY AUTOINCREMENT, server_name TEXT, server_content TEXT, server_url TEXT, socket_url TEXT, server_port INTEGER, server_district TEXT, uid INTEGER DEFAULT 0, type INTEGER DEFAULT 0, password TEXT)";
        db.execSQL(server);
    }

    private void createIndexes(SQLiteDatabase db) {
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_messageid ON chat(MessageId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_touid ON chat(ToUid)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_fromuid ON chat(FromUid)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_friend_uid ON friend(UID)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_friend_fuid ON friend(Friend_UID)");
        } catch (Exception e) {
            Log.e(TAG, "创建索引失败", e);
        }
    }

    public synchronized void closeDatabase() {
        if (mOpenCounter.decrementAndGet() == 0 && App.database != null) {
            App.database.close();
        }
    }

    public boolean isTableExists(SQLiteDatabase db, String tableName) {
        if (db == null || !db.isOpen()) return false;
        try (Cursor cursor = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", new String[]{tableName})) {
            return cursor != null && cursor.getCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isColumnExists(SQLiteDatabase db, String tableName, String columnName) {
        if (db == null || !db.isOpen()) return false;
        if (!tableName.matches("^[a-zA-Z0-9_]+$")) return false;
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null)) {
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex("name");
                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        if (columnName.equalsIgnoreCase(cursor.getString(nameIndex))) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}
