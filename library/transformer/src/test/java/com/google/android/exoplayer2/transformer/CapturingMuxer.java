/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.testutil.DumpableFormat;
import com.google.android.exoplayer2.testutil.Dumper;
import com.google.android.exoplayer2.testutil.Dumper.Dumpable;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link Dumpable} {@link Muxer} implementation that supports dumping information about all
 * interactions (for testing purposes) and forwards method calls to the underlying {@link Muxer}.
 */
public final class CapturingMuxer implements Muxer, Dumpable {

  /**
   * A {@link Muxer.Factory} for {@link CapturingMuxer} that captures and provides access to the
   * {@linkplain #create created} muxer.
   */
  public static final class Factory implements Muxer.Factory {
    private final Muxer.Factory wrappedFactory;

    @Nullable private CapturingMuxer muxer;

    public Factory() {
      this(/* maxDelayBetweenSamplesMs= */ C.TIME_UNSET);
    }

    public Factory(long maxDelayBetweenSamplesMs) {
      this.wrappedFactory = new DefaultMuxer.Factory(maxDelayBetweenSamplesMs);
    }

    /** Returns the most recently {@linkplain #create created} {@code TestMuxer}. */
    public CapturingMuxer getCreatedMuxer() {
      return checkNotNull(muxer);
    }

    @Override
    public Muxer create(String path) throws Muxer.MuxerException {
      muxer = new CapturingMuxer(wrappedFactory.create(path));
      return muxer;
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
      return wrappedFactory.getSupportedSampleMimeTypes(trackType);
    }
  }

  private final Muxer wrappedMuxer;
  private final SparseArray<ArrayList<DumpableSample>> dumpableSamplesByTrackIndex;
  private final List<Dumpable> dumpables;

  /** Creates a new test muxer. */
  private CapturingMuxer(Muxer wrappedMuxer) {
    this.wrappedMuxer = wrappedMuxer;
    dumpableSamplesByTrackIndex = new SparseArray<>();
    dumpables = new ArrayList<>();
  }

  // Muxer implementation.

  @Override
  public int addTrack(Format format) throws MuxerException {
    int trackIndex = wrappedMuxer.addTrack(format);
    dumpables.add(new DumpableFormat(format, trackIndex));
    dumpableSamplesByTrackIndex.append(trackIndex, new ArrayList<>());
    return trackIndex;
  }

  @Override
  public void writeSampleData(
      int trackIndex, ByteBuffer data, long presentationTimeUs, @C.BufferFlags int flags)
      throws MuxerException {
    dumpableSamplesByTrackIndex
        .get(trackIndex)
        .add(
            new DumpableSample(
                trackIndex,
                data,
                (flags & C.BUFFER_FLAG_KEY_FRAME) == C.BUFFER_FLAG_KEY_FRAME,
                presentationTimeUs));
    wrappedMuxer.writeSampleData(trackIndex, data, presentationTimeUs, flags);
  }

  @Override
  public void addMetadata(Metadata metadata) {
    dumpables.add(dumper -> dumper.add("container metadata", metadata));
    wrappedMuxer.addMetadata(metadata);
  }

  @Override
  public void release(boolean forCancellation) throws MuxerException {
    for (int i = 0; i < dumpableSamplesByTrackIndex.size(); i++) {
      dumpables.addAll(dumpableSamplesByTrackIndex.valueAt(i));
    }
    dumpables.add(dumper -> dumper.add("released", true));
    wrappedMuxer.release(forCancellation);
  }

  @Override
  public long getMaxDelayBetweenSamplesMs() {
    return wrappedMuxer.getMaxDelayBetweenSamplesMs();
  }

  // Dumper.Dumpable implementation.

  @Override
  public void dump(Dumper dumper) {
    for (Dumpable dumpable : dumpables) {
      dumpable.dump(dumper);
    }
  }

  private static final class DumpableSample implements Dumpable {

    private final int trackIndex;
    private final long presentationTimeUs;
    private final boolean isKeyFrame;
    private final int sampleDataHashCode;
    private final int sampleSize;

    public DumpableSample(
        int trackIndex, ByteBuffer sample, boolean isKeyFrame, long presentationTimeUs) {
      this.trackIndex = trackIndex;
      this.presentationTimeUs = presentationTimeUs;
      this.isKeyFrame = isKeyFrame;
      int initialPosition = sample.position();
      sampleSize = sample.remaining();
      byte[] data = new byte[sampleSize];
      sample.get(data);
      sample.position(initialPosition);
      sampleDataHashCode = Arrays.hashCode(data);
    }

    @Override
    public void dump(Dumper dumper) {
      dumper
          .startBlock("sample")
          .add("trackIndex", trackIndex)
          .add("dataHashCode", sampleDataHashCode)
          .add("size", sampleSize)
          .add("isKeyFrame", isKeyFrame)
          .add("presentationTimeUs", presentationTimeUs)
          .endBlock();
    }
  }
}
