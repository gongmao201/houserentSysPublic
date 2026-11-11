package com.wt.hrentSystem.common.util;

/**
 * Nginx 进程管理工具（Windows）
 * 说明：原始发布包缺失该类，HrentApplication.openNginx() 引用（当前已注释），
 * 此为重建实现，保证源码可编译。
 */
public class NginxUtils {

    private NginxUtils() {
    }

    /** 判断当前操作系统是否为 Windows */
    public static boolean judgeOs() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    /** 判断 nginx 进程是否已启动 */
    public static boolean findProcess() {
        if (!judgeOs()) {
            return false;
        }
        try {
            Process process = Runtime.getRuntime()
                    .exec(System.getenv("SystemRoot") + "/System32/tasklist.exe");
            String output = new String(readAll(process), "GBK");
            process.waitFor();
            return output.toLowerCase().contains("nginx.exe");
        } catch (Exception e) {
            return false;
        }
    }

    /** 启动 nginx */
    public static void start() {
        try {
            Runtime.getRuntime().exec("cmd /c start nginx");
        } catch (Exception e) {
            System.err.println("[NginxUtils] nginx 启动失败: " + e.getMessage());
        }
    }

    private static byte[] readAll(Process process) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = process.getInputStream().read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
