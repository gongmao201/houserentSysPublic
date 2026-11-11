package com.wt.hrentSystem.common.util;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * application.yml 读取工具
 * 说明：原始发布包缺失该类，Page、SysCommonController 等依赖其静态方法
 * loadYml(fileName) / getValue(dottedKey)，此为重建实现。
 * 依赖 spring-boot 传递引入的 snakeyaml。
 */
public class YmlUtil {

    private YmlUtil() {
    }

    private static final Properties PROPS = new Properties();
    private static boolean loaded = false;

    /**
     * 加载 classpath 下的 yml 配置（重复调用会重新加载，与原项目行为一致）
     */
    public static void loadYml(String fileName) {
        try (InputStream in = YmlUtil.class.getClassLoader().getResourceAsStream(fileName)) {
            if (in == null) {
                return;
            }
            PROPS.clear();
            Yaml yaml = new Yaml();
            Object obj = yaml.load(in);
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) obj;
                flatten(map, "");
            }
            loaded = true;
        } catch (Exception e) {
            // 保留已加载的旧配置，避免因单次读取失败影响运行
            System.err.println("[YmlUtil] 加载 " + fileName + " 失败: " + e.getMessage());
        }
    }

    private static void flatten(Map<String, Object> map, String prefix) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? String.valueOf(entry.getKey())
                    : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> child = (Map<String, Object>) value;
                flatten(child, key);
            } else {
                PROPS.setProperty(key, value == null ? "" : String.valueOf(value));
            }
        }
    }

    /**
     * 按点分键获取配置值，如 getValue("sys.name")
     */
    public static Object getValue(String key) {
        if (!loaded) {
            loadYml("application.yml");
        }
        return PROPS.getProperty(key);
    }

    /**
     * 获取字符串配置，带默认值
     */
    public static String getString(String key, String defaultValue) {
        Object v = getValue(key);
        return v == null ? defaultValue : String.valueOf(v);
    }
}
