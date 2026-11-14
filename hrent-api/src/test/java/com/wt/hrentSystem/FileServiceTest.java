package com.wt.hrentSystem;

import com.wt.hrentSystem.api.v2.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图片上传压缩回归（W5：压缩 ≤200KB + 400px 缩略图 + webp 原图兜底）。
 * 用纯 JDK ImageIO 造图，不依赖外部样本，避免“样本图太小压不出效果”的假通过。
 */
class FileServiceTest {

    private Path tempDir;

    @AfterEach
    void cleanup() throws Exception {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                            // 临时目录清理失败不影响断言
                        }
                    });
        }
    }

    @Test
    void largeJpegIsCompressedAndThumbnailed() throws Exception {
        tempDir = Files.createTempDirectory("hrent-img-");
        FileService service = new FileService();
        ReflectionTestUtils.setField(service, "localDir", tempDir.toString());

        byte[] noisy = noisyJpeg(2200, 1600);
        assertTrue(noisy.length > 200 * 1024, "样本必须大于 200KB，否则压缩断言无意义");

        MockMultipartFile file = new MockMultipartFile(
                "file", "repair.jpg", "image/jpeg", noisy);
        Map<String, Object> data = service.upload(file);

        assertEquals(Boolean.TRUE, data.get("compressed"));
        long size = ((Number) data.get("size")).longValue();
        assertTrue(size <= 200 * 1024, "压缩后应 ≤200KB，实际 " + size);
        assertTrue((Integer) data.get("width") <= 1600, "长边应缩到 1600 以内");
        assertTrue((Integer) data.get("height") <= 1600);

        String thumbUrl = (String) data.get("thumbUrl");
        assertNotNull(thumbUrl);
        assertTrue(thumbUrl.endsWith("_thumb.jpg"));
        assertTrue((Integer) data.get("thumbWidth") <= 400);
        assertTrue((Integer) data.get("thumbHeight") <= 400);

        File main = tempDir.resolve(((String) data.get("url")).replace("/images/", "")).toFile();
        File thumb = tempDir.resolve(thumbUrl.replace("/images/", "")).toFile();
        assertTrue(main.exists() && main.length() <= 200 * 1024);
        assertTrue(thumb.exists() && thumb.length() > 0);
    }

    @Test
    void smallImageIsKeptUnderLimit() throws Exception {
        tempDir = Files.createTempDirectory("hrent-img-");
        FileService service = new FileService();
        ReflectionTestUtils.setField(service, "localDir", tempDir.toString());

        MockMultipartFile file = new MockMultipartFile(
                "file", "small.png", "image/png", solidPng(200, 120));
        Map<String, Object> data = service.upload(file);
        assertEquals(Boolean.TRUE, data.get("compressed"));
        assertEquals(200, data.get("width"));
        assertEquals(120, data.get("height"));
        assertTrue(((Number) data.get("size")).longValue() <= 200 * 1024);
    }

    private byte[] noisyJpeg(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                row[x] = random.nextInt(0xFFFFFF);
            }
            img.setRGB(0, y, width, 1, row, 0, width);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    private byte[] solidPng(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(30, 120, 200, 200));
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
