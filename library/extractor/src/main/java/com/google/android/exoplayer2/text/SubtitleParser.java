/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.exoplayer2.text;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Format.CueReplacementBehavior;
import com.google.android.exoplayer2.util.Consumer;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses subtitle data into timed {@linkplain CuesWithTiming} instances.
 *
 * <p>Instances are stateful, so samples can be fed in repeated calls to {@link #parse(byte[])}, and
 * one or more complete {@link CuesWithTiming} instances will be returned when enough data has been
 * received. Due to this stateful-ness, {@link #reset()} must be called after a seek or similar
 * discontinuity in the source data.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public interface SubtitleParser {

  /** Factory for {@link SubtitleParser} instances. */
  interface Factory {

    /**
     * Returns whether the factory is able to instantiate a {@link SubtitleParser} for the given
     * {@link Format}.
     *
     * @param format The {@link Format}.
     * @return Whether the factory can instantiate a suitable {@link SubtitleParser}.
     */
    boolean supportsFormat(Format format);

    /**
     * Returns the {@link CueReplacementBehavior} of the {@link SubtitleParser} implementation that
     * handles {@code format}.
     *
     * @return The replacement behavior.
     * @throws IllegalArgumentException if {@code format} is {@linkplain #supportsFormat(Format) not
     *     supported} by this factory.
     */
    @CueReplacementBehavior
    int getCueReplacementBehavior(Format format);

    /**
     * Creates a {@link SubtitleParser} for the given {@link Format}.
     *
     * @return The {@link SubtitleParser} instance.
     * @throws IllegalArgumentException if {@code format} is {@linkplain #supportsFormat(Format) not
     *     supported} by this factory.
     */
    SubtitleParser create(Format format);
  }

  /**
   * Options to control the output behavior of {@link SubtitleParser} methods that emit their output
   * incrementally using a {@link Consumer} provided by the caller.
   */
  class OutputOptions {

    private static final OutputOptions ALL =
        new OutputOptions(C.TIME_UNSET, /* outputAllCues= */ false);

    /**
     * Cues after this time (inclusive) will be emitted first. Cues before this time might be
     * emitted later, depending on {@link #outputAllCues}.
     */
    public final long startTimeUs;

    /**
     * Whether to eventually emit all cues, or only those after {@link #startTimeUs}. Ignored if
     * {@link #startTimeUs} is not set.
     */
    public final boolean outputAllCues;

    private OutputOptions(long startTimeUs, boolean outputAllCues) {
      this.startTimeUs = startTimeUs;
      this.outputAllCues = outputAllCues;
    }

    /** Output all {@link CuesWithTiming} instances. */
    public static OutputOptions allCues() {
      return ALL;
    }

    /**
     * Only output {@link CuesWithTiming} instances where {@link CuesWithTiming#startTimeUs} is at
     * least {@code startTimeUs}.
     *
     * <p>The order in which {@link CuesWithTiming} instances are emitted is not defined.
     */
    public static OutputOptions onlyCuesAfter(long startTimeUs) {
      return new OutputOptions(startTimeUs, /* outputAllCues= */ false);
    }

    /**
     * Output {@link CuesWithTiming} where {@link CuesWithTiming#startTimeUs} is at least {@code
     * startTimeUs}, followed by the remaining {@link CuesWithTiming} instances.
     *
     * <p>Beyond this, the order in which {@link CuesWithTiming} instances are emitted is not
     * defined.
     */
    public static OutputOptions cuesAfterThenRemainingCuesBefore(long startTimeUs) {
      return new OutputOptions(startTimeUs, /* outputAllCues= */ true);
    }
  }

  /**
   * Parses {@code data} (and any data stored from previous invocations) and returns any resulting
   * complete {@link CuesWithTiming} instances.
   *
   * <p>Equivalent to {@link #parse(byte[], int, int) parse(data, 0, data.length)}.
   */
  @Nullable
  default List<CuesWithTiming> parse(byte[] data) {
    return parse(data, /* offset= */ 0, data.length);
  }

  /**
   * Parses {@code data} (and any data stored from previous invocations) and emits resulting {@link
   * CuesWithTiming} instances.
   *
   * <p>Equivalent to {@link #parse(byte[], int, int, OutputOptions, Consumer) parse(data, 0,
   * data.length, outputOptions, output)}.
   */
  default void parse(byte[] data, OutputOptions outputOptions, Consumer<CuesWithTiming> output) {
    parse(data, /* offset= */ 0, data.length, outputOptions, output);
  }

  /**
   * Parses {@code data} (and any data stored from previous invocations) and returns any resulting
   * complete {@link CuesWithTiming} instances.
   *
   * <p>Any samples not used from {@code data} will be persisted and used during subsequent calls to
   * this method.
   *
   * <p>{@link CuesWithTiming#startTimeUs} in the returned instance is derived only from the
   * provided sample data, so has to be considered together with any relevant {@link
   * Format#subsampleOffsetUs}. If the provided sample doesn't contain any timing information then
   * at most one {@link CuesWithTiming} instance will be returned, with {@link
   * CuesWithTiming#startTimeUs} set to {@link C#TIME_UNSET}, in which case {@link
   * Format#subsampleOffsetUs} <b>must</b> be {@link Format#OFFSET_SAMPLE_RELATIVE}.
   *
   * @param data The subtitle data to parse. This must contain only complete samples. For subtitles
   *     muxed inside a media container, a sample is usually defined by the container. For subtitles
   *     read from a text file, a sample is usually the entire contents of the text file.
   * @param offset The index in {@code data} to start reading from (inclusive).
   * @param length The number of bytes to read from {@code data}.
   * @return The {@linkplain CuesWithTiming} instances parsed from {@code data} (and possibly
   *     previous provided samples too), sorted in ascending order by {@link
   *     CuesWithTiming#startTimeUs}. Otherwise null if there is insufficient data to generate a
   *     complete {@link CuesWithTiming}.
   */
  @Nullable
  List<CuesWithTiming> parse(byte[] data, int offset, int length);

  /**
   * Parses {@code data} (and any data stored from previous invocations) and emits any resulting
   * complete {@link CuesWithTiming} instances via {@code output}.
   *
   * <p>Any samples not used from {@code data} will be persisted and used during subsequent calls to
   * this method.
   *
   * <p>{@link CuesWithTiming#startTimeUs} in an emitted instance is derived only from the provided
   * sample data, so has to be considered together with any relevant {@link
   * Format#subsampleOffsetUs}. If the provided sample doesn't contain any timing information then
   * at most one {@link CuesWithTiming} instance will be emitted, with {@link
   * CuesWithTiming#startTimeUs} set to {@link C#TIME_UNSET}, in which case {@link
   * Format#subsampleOffsetUs} <b>must</b> be {@link Format#OFFSET_SAMPLE_RELATIVE}.
   *
   * @param data The subtitle data to parse. This must contain only complete samples. For subtitles
   *     muxed inside a media container, a sample is usually defined by the container. For subtitles
   *     read from a text file, a sample is usually the entire contents of the text file.
   * @param offset The index in {@code data} to start reading from (inclusive).
   * @param length The number of bytes to read from {@code data}.
   * @param outputOptions Options to control how instances are emitted to {@code output}.
   * @param output A consumer for {@link CuesWithTiming} instances emitted by this method. All calls
   *     will be made on the thread that called this method, and will be completed before this
   *     method returns.
   */
  default void parse(
      byte[] data,
      int offset,
      int length,
      OutputOptions outputOptions,
      Consumer<CuesWithTiming> output) {
    List<CuesWithTiming> cuesWithTimingList = parse(data, offset, length);
    if (cuesWithTimingList == null) {
      return;
    }
    @Nullable
    List<CuesWithTiming> cuesWithTimingBeforeRequestedStartTimeUs =
        outputOptions.startTimeUs != C.TIME_UNSET && outputOptions.outputAllCues
            ? new ArrayList<>()
            : null;
    for (CuesWithTiming cuesWithTiming : cuesWithTimingList) {
      if (outputOptions.startTimeUs == C.TIME_UNSET
          || cuesWithTiming.startTimeUs >= outputOptions.startTimeUs) {
        output.accept(cuesWithTiming);
      } else if (cuesWithTimingBeforeRequestedStartTimeUs != null) {
        cuesWithTimingBeforeRequestedStartTimeUs.add(cuesWithTiming);
      }
    }
    if (cuesWithTimingBeforeRequestedStartTimeUs != null) {
      for (CuesWithTiming cuesWithTiming : cuesWithTimingBeforeRequestedStartTimeUs) {
        output.accept(cuesWithTiming);
      }
    }
  }

  /**
   * Parses {@code data} to a legacy {@link Subtitle} instance.
   *
   * <p>This method only exists temporarily to support the transition away from {@link
   * SubtitleDecoder} and {@link Subtitle}. It will be removed in a future release.
   *
   * <p>The default implementation delegates to {@link #parse(byte[], int, int, OutputOptions,
   * Consumer)}. Implementations can override this to provide a more efficient implementation if
   * desired.
   *
   * @param data The subtitle data to parse. This must contain only complete samples. For subtitles
   *     muxed inside a media container, a sample is usually defined by the container. For subtitles
   *     read from a text file, a sample is usually the entire contents of the text file.
   * @param offset The index in {@code data} to start reading from (inclusive).
   * @param length The number of bytes to read from {@code data}.
   */
  default Subtitle parseToLegacySubtitle(byte[] data, int offset, int length) {
    ImmutableList.Builder<CuesWithTiming> cuesWithTimingList = ImmutableList.builder();
    parse(data, offset, length, OutputOptions.ALL, cuesWithTimingList::add);
    return new CuesWithTimingSubtitle(cuesWithTimingList.build());
  }

  /**
   * Clears any data stored inside this parser from previous {@link #parse(byte[])} calls.
   *
   * <p>This must be called after a seek or other similar discontinuity in the source data.
   *
   * <p>The default implementation is a no-op.
   */
  default void reset() {}

  /**
   * Returns the {@link CueReplacementBehavior} for consecutive {@link CuesWithTiming} emitted by
   * this implementation.
   *
   * <p>A given instance must always return the same value from this method.
   */
  @CueReplacementBehavior
  int getCueReplacementBehavior();
}
