/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.extractor.png;

import static com.google.android.exoplayer2.C.BUFFER_FLAG_KEY_FRAME;
import static com.google.android.exoplayer2.extractor.ImageExtractorUtil.IMAGE_TRACK_ID;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SingleSampleSeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Extracts data from the PNG container format.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
// TODO: b/289989902 - Move methods of this class into ImageExtractorUtil once there are multiple
//   image extractors.
@Deprecated
public final class PngExtractor implements Extractor {

  /** Parser states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({STATE_READING_IMAGE, STATE_ENDED})
  private @interface State {}

  private static final int STATE_READING_IMAGE = 1;
  private static final int STATE_ENDED = 2;

  private static final int PNG_FILE_SIGNATURE_LENGTH = 2;
  // See PNG (Portable Network Graphics) Specification, Version 1.2, Section 12.12 and Section 3.1.
  private static final int PNG_FILE_SIGNATURE = 0x8950;
  private static final int FIXED_READ_LENGTH = 1024;

  private final ParsableByteArray scratch;

  private int size;
  private @State int state;
  private @MonotonicNonNull ExtractorOutput extractorOutput;

  /** Creates an instance. */
  public PngExtractor() {
    scratch = new ParsableByteArray(PNG_FILE_SIGNATURE_LENGTH);
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    scratch.reset(/* limit= */ PNG_FILE_SIGNATURE_LENGTH);
    input.peekFully(scratch.getData(), /* offset= */ 0, PNG_FILE_SIGNATURE_LENGTH);
    return scratch.readUnsignedShort() == PNG_FILE_SIGNATURE;
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    outputImageTrackAndSeekMap();
  }

  @Override
  public @ReadResult int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException {
    switch (state) {
      case STATE_READING_IMAGE:
        readSegment(input);
        return RESULT_CONTINUE;
      case STATE_ENDED:
        return RESULT_END_OF_INPUT;
      default:
        throw new IllegalStateException();
    }
  }

  private void readSegment(ExtractorInput input) throws IOException {
    TrackOutput trackOutput =
        checkNotNull(extractorOutput).track(IMAGE_TRACK_ID, C.TRACK_TYPE_IMAGE);
    int result = trackOutput.sampleData(input, FIXED_READ_LENGTH, /* allowEndOfInput= */ true);
    if (result == C.RESULT_END_OF_INPUT) {
      state = STATE_ENDED;
      @C.BufferFlags int flags = BUFFER_FLAG_KEY_FRAME;
      trackOutput.sampleMetadata(
          /* timeUs= */ 0, flags, size, /* offset= */ 0, /* cryptoData= */ null);
      size = 0;
    } else {
      size += result;
    }
  }

  @RequiresNonNull("this.extractorOutput")
  private void outputImageTrackAndSeekMap() {
    TrackOutput trackOutput =
        checkNotNull(extractorOutput).track(IMAGE_TRACK_ID, C.TRACK_TYPE_IMAGE);
    trackOutput.format(
        new Format.Builder()
            .setContainerMimeType(MimeTypes.IMAGE_PNG)
            .setTileCountHorizontal(1)
            .setTileCountVertical(1)
            .build());
    extractorOutput.endTracks();
    extractorOutput.seekMap(new SingleSampleSeekMap(/* durationUs= */ C.TIME_UNSET));
    state = STATE_READING_IMAGE;
  }

  @Override
  public void seek(long position, long timeUs) {
    if (position == 0) {
      state = STATE_READING_IMAGE;
    }
    if (state == STATE_READING_IMAGE) {
      size = 0;
    }
  }

  @Override
  public void release() {
    // Do nothing.
  }
}
