package org.grimmory.epub4j.optimize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaType;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.epub.BookProcessor;

/**
 * Compresses bitmap images (JPEG, PNG) in an EPUB. Uses native libjpeg-turbo/libpng when available,
 * falling back to Java ImageIO.
 *
 * <p>Safety rule: if the compressed output is larger than the original, the original is kept. This
 * mirrors Calibre's optimization behavior.
 */
public class ImageCompressor implements BookProcessor {

  private static final System.Logger log = System.getLogger(ImageCompressor.class.getName());

  // Cached method handles avoid per-call reflection overhead. The epub4j-native module is
  // optional, so handles are resolved reflectively once and reused.
  private static final boolean NATIVE_IMAGE_AVAILABLE;
  private static final MethodHandle MH_HAS_JPEG;
  private static final MethodHandle MH_HAS_PNG;
  private static final MethodHandle MH_COMPRESS_JPEG;
  private static final MethodHandle MH_OPTIMIZE_JPEG;
  private static final MethodHandle MH_OPTIMIZE_PNG;
  private static final MethodHandle MH_TO_BYTE_ARRAY;
  private static final MethodHandle MH_CLOSE;

  static {
    boolean available = false;
    MethodHandle hasJpeg = null;
    MethodHandle hasPng = null;
    MethodHandle compressJpeg = null;
    MethodHandle optimizeJpeg = null;
    MethodHandle optimizePng = null;
    MethodHandle toByteArray = null;
    MethodHandle closeHandle = null;
    try {
      Class<?> nip = Class.forName("org.grimmory.epub4j.native_parsing.NativeImageProcessor");
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      var isAvail = lookup.findStatic(nip, "isAvailable", MethodType.methodType(boolean.class));
      available = (boolean) isAvail.invokeExact();
      if (available) {
        hasJpeg = lookup.findStatic(nip, "hasJpeg", MethodType.methodType(boolean.class));
        hasPng = lookup.findStatic(nip, "hasPng", MethodType.methodType(boolean.class));
        compressJpeg =
            lookup.findStatic(
                nip,
                "compressJpeg",
                MethodType.methodType(
                    lookup.findClass(
                        "org.grimmory.epub4j.native_parsing.NativeImageProcessor$ImageData"),
                    byte[].class,
                    int.class,
                    boolean.class));
        optimizeJpeg =
            lookup.findStatic(
                nip,
                "optimizeJpeg",
                MethodType.methodType(
                    lookup.findClass(
                        "org.grimmory.epub4j.native_parsing.NativeImageProcessor$ImageData"),
                    byte[].class));
        optimizePng =
            lookup.findStatic(
                nip,
                "optimizePng",
                MethodType.methodType(
                    lookup.findClass(
                        "org.grimmory.epub4j.native_parsing.NativeImageProcessor$ImageData"),
                    byte[].class,
                    boolean.class));
        Class<?> imageDataClass =
            lookup.findClass("org.grimmory.epub4j.native_parsing.NativeImageProcessor$ImageData");
        toByteArray =
            lookup.findVirtual(imageDataClass, "toByteArray", MethodType.methodType(byte[].class));
        closeHandle =
            lookup.findVirtual(imageDataClass, "close", MethodType.methodType(void.class));
      }
    } catch (Throwable e) {
      // Native module not available; all handles remain null and NATIVE_IMAGE_AVAILABLE stays false
      log.log(System.Logger.Level.DEBUG, "Native image module unavailable: " + e.getMessage());
    }
    NATIVE_IMAGE_AVAILABLE = available;
    MH_HAS_JPEG = hasJpeg;
    MH_HAS_PNG = hasPng;
    MH_COMPRESS_JPEG = compressJpeg;
    MH_OPTIMIZE_JPEG = optimizeJpeg;
    MH_OPTIMIZE_PNG = optimizePng;
    MH_TO_BYTE_ARRAY = toByteArray;
    MH_CLOSE = closeHandle;
  }

  private final OptimizationConfig config;

  public ImageCompressor(OptimizationConfig config) {
    this.config = config;
  }

  @Override
  public Book processBook(Book book) {
    if (!config.compressImages()) {
      return book;
    }
    for (Resource resource : book.getResources().getAll()) {
      MediaType mt = resource.getMediaType();
      if (mt == null || !config.imageMediaTypes().contains(mt)) {
        continue;
      }
      try {
        compressResource(resource);
      } catch (IOException e) {
        log.log(
            System.Logger.Level.DEBUG,
            "Image compression skipped for " + resource.getHref() + ": " + e.getMessage());
      }
    }
    return book;
  }

  private void compressResource(Resource resource) throws IOException {
    byte[] original = resource.getData();
    if (original == null || original.length == 0) {
      return;
    }

    byte[] compressed;
    if (resource.getMediaType() == MediaTypes.JPG) {
      compressed = compressJpeg(original, config.jpegQuality());
    } else if (resource.getMediaType() == MediaTypes.PNG) {
      compressed = compressPng(original);
    } else {
      return;
    }

    if (compressed != null && compressed.length < original.length) {
      resource.setData(compressed);
      log.log(
          System.Logger.Level.DEBUG,
          "Compressed "
              + resource.getHref()
              + ": "
              + original.length
              + " -> "
              + compressed.length
              + " bytes");
    }
  }

  private byte[] compressJpeg(byte[] data, int quality) throws IOException {
    if (NATIVE_IMAGE_AVAILABLE) {
      return compressJpegNative(data, quality);
    }
    return compressJpegImageIO(data, quality);
  }

  private byte[] compressPng(byte[] data) throws IOException {
    if (NATIVE_IMAGE_AVAILABLE) {
      return compressPngNative(data);
    }
    return compressPngImageIO(data);
  }

  private static byte[] compressJpegNative(byte[] data, int quality) {
    try {
      if (!(boolean) MH_HAS_JPEG.invokeExact()) {
        return null;
      }
      Object result;
      if (quality < 100) {
        result = MH_COMPRESS_JPEG.invoke(data, quality, true);
      } else {
        result = MH_OPTIMIZE_JPEG.invoke(data);
      }
      return extractAndClose(result);
    } catch (Throwable e) {
      log.log(System.Logger.Level.DEBUG, "Native JPEG compression unavailable: " + e.getMessage());
      return null;
    }
  }

  private static byte[] compressPngNative(byte[] data) {
    try {
      if (!(boolean) MH_HAS_PNG.invokeExact()) {
        return null;
      }
      Object result = MH_OPTIMIZE_PNG.invoke(data, true);
      return extractAndClose(result);
    } catch (Throwable e) {
      log.log(System.Logger.Level.DEBUG, "Native PNG compression unavailable: " + e.getMessage());
      return null;
    }
  }

  /**
   * Extracts byte data from a native ImageData result and ensures the native resource is freed.
   * Uses cached MethodHandles since epub4j-native is an optional dependency.
   */
  private static byte[] extractAndClose(Object nativeResult) throws Throwable {
    try {
      byte[] bytes = (byte[]) MH_TO_BYTE_ARRAY.invoke(nativeResult);
      if (bytes != null && bytes.length > 0) {
        return bytes;
      }
      return null;
    } finally {
      MH_CLOSE.invoke(nativeResult);
    }
  }

  private static byte[] compressJpegImageIO(byte[] data, int quality) throws IOException {
    var image = ImageIO.read(new ByteArrayInputStream(data));
    if (image == null) {
      return null;
    }

    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    if (!writers.hasNext()) {
      return null;
    }
    ImageWriter writer = writers.next();
    try {
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(quality / 100f);
      if (param.canWriteProgressive()) {
        param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
      try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
        writer.setOutput(ios);
        writer.write(null, new IIOImage(image, null, null), param);
      }
      return baos.toByteArray();
    } finally {
      writer.dispose();
    }
  }

  private static byte[] compressPngImageIO(byte[] data) throws IOException {
    // PNG re-encoding via ImageIO applies default optimizations (filter selection, compression).
    // For deeper optimization, the native libpng binding strips ancillary chunks and
    // tries harder filter/compression combos.
    var image = ImageIO.read(new ByteArrayInputStream(data));
    if (image == null) {
      return null;
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
    if (!ImageIO.write(image, "png", baos)) {
      return null;
    }
    return baos.toByteArray();
  }
}
