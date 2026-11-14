package com.wt.hrentSystem.api.v2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * v2 文件上传（docs/13 §4.7、docs/14 FR-REPAIR-05）。
 *
 * 已实现：扩展名/Content-Type 白名单、5MB 上限、UUID 重命名（防路径穿越）、目录配置化、
 * 服务端压缩（长边 1600、目标 ≤200KB）、400px 缩略图（JDK ImageIO，无额外依赖）。
 * webp 因 JDK 无内置解码器，按原图落盘（compressed=false，thumbUrl=url），不阻断上传。
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final long TARGET_BYTES = 200L * 1024;
    private static final int MAX_EDGE = 1600;
    private static final int THUMB_EDGE = 400;
    private static final int MIN_EDGE = 640;
    /** 防解压炸弹：40MP 以上直接拒绝 */
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Set<String> EXTS = new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "webp"));
    private static final Set<String> TYPES = new HashSet<>(
            Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp"));

    @Value("${file.local:./static/images}")
    private String localDir;

    public Map<String, Object> upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.bad("上传文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw ApiException.bad("文件不能超过 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw ApiException.bad("仅支持 jpg / jpeg / png / webp 图片");
        }
        String original = file.getOriginalFilename();
        String ext = extensionOf(original);
        if (ext == null || !EXTS.contains(ext)) {
            throw ApiException.bad("文件扩展名不合法，仅支持 jpg / jpeg / png / webp");
        }

        byte[] raw;
        try {
            raw = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ApiCodes.SERVER_ERROR, "读取上传文件失败");
        }

        BufferedImage source = null;
        try {
            source = ImageIO.read(new ByteArrayInputStream(raw));
        } catch (IOException e) {
            log.warn("v2 上传图片解码失败 traceId=" + TraceIds.get(), e);
        }

        Path dir = Paths.get(localDir).toAbsolutePath().normalize();
        String id = UUID.randomUUID().toString().replace("-", "");

        // webp / 无法解码：原图落盘，显式标记未压缩，不伪造尺寸
        if (source == null) {
            if ("webp".equals(ext)) {
                return saveOriginal(dir, id, ext, raw);
            }
            throw ApiException.bad("图片无法解析，请确认文件未损坏（当前仅压缩 jpg/png）");
        }
        long pixels = (long) source.getWidth() * source.getHeight();
        if (pixels > MAX_PIXELS) {
            throw ApiException.bad("图片像素过大（" + source.getWidth() + "×" + source.getHeight() + "），请压缩后再传");
        }

        byte[] mainBytes = compressToTarget(source);
        BufferedImage mainImage = decode(mainBytes);
        if (mainImage == null) {
            throw new ApiException(ApiCodes.SERVER_ERROR, "图片压缩失败");
        }

        String mainName = id + ".jpg";
        String thumbName = id + "_thumb.jpg";
        byte[] thumbBytes = encodeJpeg(scaleToMax(source, THUMB_EDGE), 0.70f);
        BufferedImage thumbImage = decode(thumbBytes);

        try {
            Files.createDirectories(dir);
            writeSafely(dir, mainName, mainBytes);
            writeSafely(dir, thumbName, thumbBytes);
        } catch (IOException e) {
            log.error("v2 上传落盘失败 traceId=" + TraceIds.get() + " dir=" + dir, e);
            throw new ApiException(ApiCodes.SERVER_ERROR, "文件保存失败，请稍后重试");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        // 与存量 WebMvcConfig 的 /images/** 静态映射对齐
        data.put("url", "/images/" + mainName);
        data.put("thumbUrl", "/images/" + thumbName);
        data.put("size", mainBytes.length);
        data.put("originalSize", raw.length);
        data.put("width", mainImage.getWidth());
        data.put("height", mainImage.getHeight());
        data.put("thumbWidth", thumbImage == null ? null : thumbImage.getWidth());
        data.put("thumbHeight", thumbImage == null ? null : thumbImage.getHeight());
        data.put("compressed", Boolean.TRUE);
        return data;
    }

    /** jpg/png 统一转 JPEG：先按长边 1600 缩放，再按质量阶梯压到 ≤200KB */
    private byte[] compressToTarget(BufferedImage source) {
        BufferedImage img = scaleToMax(source, MAX_EDGE);
        float[] qualities = {0.85f, 0.75f, 0.65f, 0.55f, 0.45f};
        byte[] best = encodeJpeg(img, qualities[qualities.length - 1]);
        for (float q : qualities) {
            byte[] bytes = encodeJpeg(img, q);
            if (bytes.length < best.length) {
                best = bytes;
            }
            if (bytes.length <= TARGET_BYTES) {
                return bytes;
            }
        }
        // 质量降到 0.45 仍超标：按 0.8 比例继续缩，最低到 640 长边
        while (Math.max(img.getWidth(), img.getHeight()) > MIN_EDGE) {
            int w = Math.max(1, (int) (img.getWidth() * 0.8));
            int h = Math.max(1, (int) (img.getHeight() * 0.8));
            img = scaleToSize(img, w, h);
            byte[] bytes = encodeJpeg(img, 0.55f);
            if (bytes.length < best.length) {
                best = bytes;
            }
            if (bytes.length <= TARGET_BYTES) {
                return bytes;
            }
        }
        return best;
    }

    private BufferedImage scaleToMax(BufferedImage source, int maxEdge) {
        int w = source.getWidth();
        int h = source.getHeight();
        int longEdge = Math.max(w, h);
        if (longEdge <= maxEdge) {
            return toRgb(source);
        }
        double ratio = (double) maxEdge / longEdge;
        int targetW = Math.max(1, (int) Math.round(w * ratio));
        int targetH = Math.max(1, (int) Math.round(h * ratio));
        return scaleToSize(source, targetW, targetH);
    }

    private BufferedImage scaleToSize(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** 透明 PNG 铺白底转 RGB，避免 JPEG 编码报错或出现黑块 */
    private BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        return scaleToSize(source, source.getWidth(), source.getHeight());
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new ApiException(ApiCodes.SERVER_ERROR, "当前 JVM 缺少 JPEG 编码器");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
            ios.flush();
        } catch (IOException e) {
            throw new ApiException(ApiCodes.SERVER_ERROR, "图片压缩失败");
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private BufferedImage decode(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return null;
        }
    }

    /** webp 原图落盘：不做压缩，返回原始大小与同值缩略图 */
    private Map<String, Object> saveOriginal(Path dir, String id, String ext, byte[] raw) {
        String name = id + "." + ext;
        try {
            Files.createDirectories(dir);
            writeSafely(dir, name, raw);
        } catch (IOException e) {
            log.error("v2 上传落盘失败 traceId=" + TraceIds.get() + " dir=" + dir, e);
            throw new ApiException(ApiCodes.SERVER_ERROR, "文件保存失败，请稍后重试");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("url", "/images/" + name);
        data.put("thumbUrl", "/images/" + name);
        data.put("size", raw.length);
        data.put("originalSize", raw.length);
        data.put("width", null);
        data.put("height", null);
        data.put("compressed", Boolean.FALSE);
        return data;
    }

    private void writeSafely(Path dir, String name, byte[] bytes) throws IOException {
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir)) {
            throw ApiException.bad("非法文件路径");
        }
        Files.write(target, bytes);
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
