package org.grimmory.epub4j.util;

import java.io.IOException;
import java.io.OutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class NoCloseOutputStreamTest {

  @Mock private OutputStream outputStream;

  private NoCloseOutputStream noCloseOutputStream;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    this.noCloseOutputStream = new NoCloseOutputStream(outputStream);
  }

  @Test
  public void testWrite() throws IOException {
    // given

    // when
    noCloseOutputStream.write(17);

    // then
    Mockito.verify(outputStream).write(17);
    Mockito.verifyNoMoreInteractions(outputStream);
  }

  @Test
  public void testClose() {
    // given

    // when
    noCloseOutputStream.close();

    // then
    Mockito.verifyNoMoreInteractions(outputStream);
  }

  @Test
  public void testWriteClose() throws IOException {
    // given

    // when
    noCloseOutputStream.write(17);
    noCloseOutputStream.close();

    // then
    Mockito.verify(outputStream).write(17);
    Mockito.verifyNoMoreInteractions(outputStream);
  }
}
