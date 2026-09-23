package Utils;

import android.widget.EditText;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class StringUtils {
    public static String sqliteEscape(String keyWord){
        // keyWord = keyWord.replace("/", "//");
        keyWord = keyWord.replace("'", "’");
        keyWord = keyWord.replace("'", "”");
        return keyWord;
    }

    // 按Unicode码点截取（支持所有字符）
    public static String safeSubstring(String str, int startCodePoint, int endCodePoint) {
        String _str=str.codePoints()
                .skip(startCodePoint)
                .limit(endCodePoint - startCodePoint)
                .collect(StringBuilder::new,
                        StringBuilder::appendCodePoint,
                        StringBuilder::append)
                .toString();
        if (_str.length()<str.length()) _str=_str+"...";
        return _str;
    }

    /**
     * MD5 标准加密算法 (Java 实现)
     */
    public static String convertToMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());

            // 将字节数组转换为 16 进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "";
        }
    }


}
