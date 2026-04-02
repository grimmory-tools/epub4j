/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Originally from epub4j (https://github.com/documentnode/epub4j)
 * Copyright (C) Paul Siegmund and epub4j contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grimmory.epub4j.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.VFS;
import org.grimmory.epub4j.domain.MediaType;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;

/**
 * Utitilies for making working with apache commons VFS easier.
 *
 * @author paul
 */
public class VFSUtil {

  private static final System.Logger log = System.getLogger(VFSUtil.class.getName());

  public static Resource createResource(FileObject rootDir, FileObject file, String inputEncoding)
      throws IOException {
    MediaType mediaType = MediaTypes.determineMediaType(file.getName().getBaseName());
    if (mediaType == null) {
      return null;
    }
    String href = calculateHref(rootDir, file);
    byte[] data;
    try (InputStream in = file.getContent().getInputStream()) {
      data = IOUtils.toByteArray(in);
    }
    Resource result = new Resource(null, data, href, mediaType);
    result.setInputEncoding(inputEncoding);
    return result;
  }

  public static String calculateHref(FileObject rootDir, FileObject currentFile) {
    String result =
        currentFile.getName().toString().substring(rootDir.getName().toString().length() + 1);
    result += ".html";
    return result;
  }

  /**
   * First tries to load the inputLocation via VFS; if that doesn't work it tries to load it as a
   * local File
   *
   * @param inputLocation
   * @return the FileObject referred to by the inputLocation
   */
  public static FileObject resolveFileObject(String inputLocation) {
    FileObject result = null;
    try {
      result = VFS.getManager().resolveFile(inputLocation);
    } catch (Exception e) {
      try {
        result = VFS.getManager().resolveFile(new File("."), inputLocation);
      } catch (Exception e1) {
        log.log(System.Logger.Level.ERROR, e.getMessage(), e);
        log.log(System.Logger.Level.ERROR, e1.getMessage(), e);
      }
    }
    return result;
  }

  /**
   * First tries to load the inputLocation via VFS; if that doesn't work it tries to load it as a
   * local File
   *
   * @param inputLocation
   * @return the InputStream referred to by the inputLocation
   */
  public static InputStream resolveInputStream(String inputLocation) {
    InputStream result = null;
    try {
      FileObject fileObject = VFS.getManager().resolveFile(inputLocation);
      result = new FileObjectInputStream(fileObject.getContent().getInputStream(), fileObject);
    } catch (Exception e) {
      try {
        result = new FileInputStream(inputLocation);
      } catch (FileNotFoundException e1) {
        log.log(System.Logger.Level.ERROR, e.getMessage(), e);
        log.log(System.Logger.Level.ERROR, e1.getMessage(), e);
      }
    }
    return result;
  }

  private static final class FileObjectInputStream extends FilterInputStream {
    private final FileObject fileObject;

    private FileObjectInputStream(InputStream delegate, FileObject fileObject) {
      super(delegate);
      this.fileObject = fileObject;
    }

    @Override
    public void close() throws IOException {
      IOException closeError = null;
      try {
        super.close();
      } catch (IOException e) {
        closeError = e;
      }
      try {
        fileObject.close();
      } catch (Exception e) {
        if (closeError == null) {
          closeError = new IOException("Failed to close VFS file object", e);
        } else {
          closeError.addSuppressed(e);
        }
      }
      if (closeError != null) {
        throw closeError;
      }
    }
  }
}
