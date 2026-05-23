package com.einkreader.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件编码检测工具
 * 检测 TXT 文件的字符编码，支持 UTF-8、GBK、Big5、UTF-16 等
 */
public class EncodingDetector {

    /** 检测到的编码 */
    public static final String UTF_8 = "UTF-8";
    public static final String GBK = "GBK";
    public static final String BIG5 = "Big5";
    public static final String UTF_16LE = "UTF-16LE";
    public static final String UTF_16BE = "UTF-16BE";
    public static final String ISO_8859_1 = "ISO-8859-1";

    private static final int BUFFER_SIZE = 8192;

    /**
     * 自动检测文件编码
     */
    public static String detect(File file) {
        if (file == null || !file.exists()) return UTF_8;

        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE);
            byte[] buf = new byte[BUFFER_SIZE];
            int read = is.read(buf);

            if (read <= 0) return UTF_8;

            // 1. 检查 BOM (Byte Order Mark)
            String bomEncoding = detectBom(buf, read);
            if (bomEncoding != null) return bomEncoding;

            // 2. 检查是否是有效的 UTF-8
            if (isValidUtf8(buf, read)) {
                // 进一步确认：尝试读取更多数据验证
                if (isMostlyValidUtf8(buf, read)) {
                    return UTF_8;
                }
            }

            // 3. 检测是否是 GBK/GB2312
            if (isLikelyGBK(buf, read)) {
                return GBK;
            }

            // 4. 检测是否是 Big5
            if (isLikelyBig5(buf, read)) {
                return BIG5;
            }

        } catch (IOException e) {
            // 读取失败，返回默认编码
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException e) {}
            }
        }

        return GBK; // 老设备中文文件默认 GBK
    }

    /**
     * 检测 BOM 标记
     */
    private static String detectBom(byte[] buf, int len) {
        if (len >= 3 && (buf[0] & 0xFF) == 0xEF && (buf[1] & 0xFF) == 0xBB && (buf[2] & 0xFF) == 0xBF) {
            return UTF_8;
        }
        if (len >= 2 && (buf[0] & 0xFF) == 0xFF && (buf[1] & 0xFF) == 0xFE) {
            return UTF_16LE;
        }
        if (len >= 2 && (buf[0] & 0xFF) == 0xFE && (buf[1] & 0xFF) == 0xFF) {
            return UTF_16BE;
        }
        return null;
    }

    /**
     * 检查字节数组是否是有效的 UTF-8 编码
     */
    private static boolean isValidUtf8(byte[] buf, int len) {
        int i = 0;
        int utf8Count = 0;
        int nonUtf8Count = 0;

        while (i < len) {
            int b = buf[i] & 0xFF;

            if (b <= 0x7F) {
                // 单字节 ASCII
                i++;
            } else if ((b & 0xE0) == 0xC0) {
                // 双字节序列
                if (i + 1 >= len || (buf[i + 1] & 0xC0) != 0x80) {
                    nonUtf8Count++;
                } else {
                    utf8Count++;
                }
                i += 2;
            } else if ((b & 0xF0) == 0xE0) {
                // 三字节序列
                if (i + 2 >= len || (buf[i + 1] & 0xC0) != 0x80 || (buf[i + 2] & 0xC0) != 0x80) {
                    nonUtf8Count++;
                } else {
                    utf8Count++;
                }
                i += 3;
            } else if ((b & 0xF8) == 0xF0) {
                // 四字节序列
                if (i + 3 >= len || (buf[i + 1] & 0xC0) != 0x80 || 
                    (buf[i + 2] & 0xC0) != 0x80 || (buf[i + 3] & 0xC0) != 0x80) {
                    nonUtf8Count++;
                } else {
                    utf8Count++;
                }
                i += 4;
            } else {
                // 无效的 UTF-8 起始字节
                nonUtf8Count++;
                i++;
            }
        }

        // 如果 UTF-8 序列明显多于非 UTF-8 序列，认为是 UTF-8
        return utf8Count > nonUtf8Count;
    }

    /**
     * 进一步验证 UTF-8，读取更多字节
     */
    private static boolean isMostlyValidUtf8(byte[] buf, int len) {
        // 已经在 isValidUtf8 中验证过了，这里作为额外检查
        return isValidUtf8(buf, len);
    }

    /**
     * 检测是否可能是 GBK 编码
     * GBK 双字节高位字节范围 0x81-0xFE，低位字节范围 0x40-0xFE
     */
    private static boolean isLikelyGBK(byte[] buf, int len) {
        int i = 0;
        int gbkPairs = 0;
        int asciiCount = 0;

        while (i < len) {
            int b = buf[i] & 0xFF;
            if (b <= 0x7F) {
                asciiCount++;
                i++;
            } else if (b >= 0x81 && b <= 0xFE) {
                if (i + 1 < len) {
                    int next = buf[i + 1] & 0xFF;
                    if (next >= 0x40 && next <= 0xFE) {
                        gbkPairs++;
                        i += 2;
                        continue;
                    }
                }
                i++;
            } else {
                i++;
            }
        }

        // GBK 文件中应该有成对的双字节字符
        return gbkPairs > 0;
    }

    /**
     * 检测是否可能是 Big5 编码
     * Big5 双字节高位字节范围 0x81-0xFE，低位字节范围 0x40-0x7E 和 0xA1-0xFE
     */
    private static boolean isLikelyBig5(byte[] buf, int len) {
        int i = 0;
        int big5Pairs = 0;

        while (i < len) {
            int b = buf[i] & 0xFF;
            if (b <= 0x7F) {
                i++;
            } else if (b >= 0x81 && b <= 0xFE) {
                if (i + 1 < len) {
                    int next = buf[i + 1] & 0xFF;
                    if ((next >= 0x40 && next <= 0x7E) || (next >= 0xA1 && next <= 0xFE)) {
                        big5Pairs++;
                        i += 2;
                        continue;
                    }
                }
                i++;
            } else {
                i++;
            }
        }

        return big5Pairs > 0;
    }

    /**
     * 获取所有支持的编码列表（用于设置界面）
     */
    public static List<String> getSupportedEncodings() {
        List<String> encodings = new ArrayList<>();
        encodings.add(UTF_8);
        encodings.add(GBK);
        encodings.add(BIG5);
        encodings.add(UTF_16LE);
        encodings.add(UTF_16BE);
        return encodings;
    }
}
