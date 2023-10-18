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
package com.google.android.exoplayer2.video;

import static com.google.android.exoplayer2.util.Util.msToUs;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.os.SystemClock;
import android.view.Surface;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controls the releasing of video frames.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class VideoFrameReleaseControl {

  /**
   * The frame release action returned by {@link #getFrameReleaseAction(long, long, long, long,
   * boolean, FrameReleaseInfo)}.
   *
   * <p>One of {@link #FRAME_RELEASE_IMMEDIATELY}, {@link #FRAME_RELEASE_SCHEDULED}, {@link
   * #FRAME_RELEASE_DROP}, {@link #FRAME_RELEASE_DROP_TO_KEYFRAME}, {@link ##FRAME_RELEASE_SKIP} or
   * {@link #FRAME_RELEASE_TRY_AGAIN_LATER}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    FRAME_RELEASE_IMMEDIATELY,
    FRAME_RELEASE_SCHEDULED,
    FRAME_RELEASE_DROP,
    FRAME_RELEASE_DROP_TO_KEYFRAME,
    FRAME_RELEASE_SKIP,
    FRAME_RELEASE_SKIP_TO_KEYFRAME,
    FRAME_RELEASE_TRY_AGAIN_LATER
  })
  public @interface FrameReleaseAction {}

  /** Signals a frame should be released immediately. */
  public static final int FRAME_RELEASE_IMMEDIATELY = 0;

  /**
   * Signals a frame should be scheduled for release. The release timestamp will be returned by
   * {@link FrameReleaseInfo#getReleaseTimeNs()}.
   */
  public static final int FRAME_RELEASE_SCHEDULED = 1;

  /** Signals a frame should be dropped. */
  public static final int FRAME_RELEASE_DROP = 2;

  /** Signals frames up to the next key-frame should be dropped. */
  public static final int FRAME_RELEASE_DROP_TO_KEYFRAME = 3;

  /** Signals that a frame should be skipped. */
  public static final int FRAME_RELEASE_SKIP = 4;

  /** Signals that frames up to the next key-frame should be skipped. */
  public static final int FRAME_RELEASE_SKIP_TO_KEYFRAME = 5;

  /** Signals that a frame should not be released and the renderer should try again later. */
  public static final int FRAME_RELEASE_TRY_AGAIN_LATER = 6;

  /** Per {@link FrameReleaseAction} metadata. */
  public static class FrameReleaseInfo {
    private long earlyUs;
    private long releaseTimeNs;

    /** Resets this instances state. */
    public FrameReleaseInfo() {
      earlyUs = C.TIME_UNSET;
      releaseTimeNs = C.TIME_UNSET;
    }

    /**
     * Returns this frame's early time compared to the playback position, before any release time
     * adjustment to the screen vsync slots.
     */
    public long getEarlyUs() {
      return earlyUs;
    }

    /**
     * Returns the release time for the frame, in nanoseconds, or {@link C#TIME_UNSET} if the frame
     * should not be released yet.
     */
    public long getReleaseTimeNs() {
      return releaseTimeNs;
    }

    private void reset() {
      earlyUs = C.TIME_UNSET;
      releaseTimeNs = C.TIME_UNSET;
    }
  }

  /** Decides whether a frame should be forced to be released, or dropped. */
  public interface FrameTimingEvaluator {
    /**
     * Whether a frame should be forced for release.
     *
     * @param earlyUs The time until the buffer should be presented in microseconds. A negative
     *     value indicates that the buffer is late.
     * @param elapsedSinceLastReleaseUs The elapsed time since the last frame was released, in
     *     microseconds.
     * @return Whether the video frame should be force released.
     */
    boolean shouldForceReleaseFrame(long earlyUs, long elapsedSinceLastReleaseUs);

    /**
     * Returns whether the frame should be dropped.
     *
     * @param earlyUs The time until the buffer should be presented in microseconds. A negative
     *     value indicates that the buffer is late.
     * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
     *     measured at the start of the current iteration of the rendering loop.
     * @param isLastFrame Whether the buffer is the last buffer in the current stream.
     */
    boolean shouldDropFrame(long earlyUs, long elapsedRealtimeUs, boolean isLastFrame);

    /**
     * Returns whether to drop all frames starting from this frame to the keyframe at or after the
     * current playback position, if possible.
     *
     * @param earlyUs The time until the current buffer should be presented in microseconds. A
     *     negative value indicates that the buffer is late.
     * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
     *     measured at the start of the current iteration of the rendering loop.
     * @param isLastFrame Whether the frame is the last frame in the current stream.
     */
    boolean shouldDropFramesToKeyframe(long earlyUs, long elapsedRealtimeUs, boolean isLastFrame);

    /** The default timing evaluator. */
    FrameTimingEvaluator DEFAULT =
        new FrameTimingEvaluator() {
          @Override
          public boolean shouldForceReleaseFrame(long earlyUs, long elapsedSinceLastReleaseUs) {
            // Force render late buffers every 100ms to avoid frozen video effect.
            return earlyUs < MIN_EARLY_US_LATE_THRESHOLD && elapsedSinceLastReleaseUs > 100_000;
          }

          @Override
          public boolean shouldDropFrame(
              long earlyUs, long elapsedRealtimeUs, boolean isLastFrame) {
            return earlyUs < MIN_EARLY_US_LATE_THRESHOLD && !isLastFrame;
          }

          @Override
          public boolean shouldDropFramesToKeyframe(
              long earlyUs, long elapsedRealtimeUs, boolean isLastFrame) {
            return earlyUs < MIN_EARLY_US_VERY_LATE_THRESHOLD && !isLastFrame;
          }
        };
  }

  /** The earliest time threshold, in microseconds, after which a frame is considered late. */
  private static final long MIN_EARLY_US_LATE_THRESHOLD = -30_000;

  /** The earliest time threshold, in microseconds, after which a frame is considered very late. */
  private static final long MIN_EARLY_US_VERY_LATE_THRESHOLD = -500_000;

  /** The maximum earliest time, in microseconds, to release a frame on the surface. */
  private static final long MAX_EARLY_US_THRESHOLD = 50_000;

  private final VideoFrameReleaseHelper frameReleaseHelper;
  private final long allowedJoiningTimeMs;

  private FrameTimingEvaluator frameTimingEvaluator;
  private boolean started;
  private @C.FirstFrameState int firstFrameState;
  private long initialPositionUs;
  private long lastReleaseRealtimeUs;
  private long lastPresentationTimeUs;
  private long joiningDeadlineMs;
  private float playbackSpeed;
  private Clock clock;

  /**
   * Creates an instance.
   *
   * @param applicationContext The application context.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which the renderer can
   *     attempt to seamlessly join an ongoing playback.
   */
  public VideoFrameReleaseControl(Context applicationContext, long allowedJoiningTimeMs) {
    frameReleaseHelper = new VideoFrameReleaseHelper(applicationContext);
    this.allowedJoiningTimeMs = allowedJoiningTimeMs;
    this.frameTimingEvaluator = FrameTimingEvaluator.DEFAULT;
    firstFrameState = C.FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED;
    initialPositionUs = C.TIME_UNSET;
    lastPresentationTimeUs = C.TIME_UNSET;
    joiningDeadlineMs = C.TIME_UNSET;
    playbackSpeed = 1f;
    clock = Clock.DEFAULT;
  }

  /** Sets the {@link FrameTimingEvaluator}. */
  public void setFrameTimingEvaluator(FrameTimingEvaluator frameTimingEvaluator) {
    this.frameTimingEvaluator = frameTimingEvaluator;
  }

  /** Called when the renderer is enabled. */
  public void onEnabled(boolean releaseFirstFrameBeforeStarted) {
    firstFrameState =
        releaseFirstFrameBeforeStarted
            ? C.FIRST_FRAME_NOT_RENDERED
            : C.FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED;
  }

  /** Called when the renderer is disabled. */
  public void onDisabled() {
    lowerFirstFrameState(C.FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED);
  }

  /** Called when the renderer is started. */
  public void onStarted() {
    started = true;
    lastReleaseRealtimeUs = msToUs(clock.elapsedRealtime());
    frameReleaseHelper.onStarted();
  }

  /** Called when the renderer is stopped. */
  public void onStopped() {
    started = false;
    joiningDeadlineMs = C.TIME_UNSET;
    frameReleaseHelper.onStopped();
  }

  /** Called when the renderer processed a stream change. */
  public void onProcessedStreamChange() {
    lowerFirstFrameState(C.FIRST_FRAME_NOT_RENDERED_AFTER_STREAM_CHANGE);
  }

  /** Called when the display surface changed. */
  public void setOutputSurface(@Nullable Surface outputSurface) {
    frameReleaseHelper.onSurfaceChanged(outputSurface);
    lowerFirstFrameState(C.FIRST_FRAME_NOT_RENDERED);
  }

  /** Sets the frame rate. */
  public void setFrameRate(float frameRate) {
    frameReleaseHelper.onFormatChanged(frameRate);
  }

  /**
   * Called when a frame have been released.
   *
   * @return Whether this is the first released frame.
   */
  public boolean onFrameReleasedIsFirstFrame() {
    boolean firstFrame = firstFrameState != C.FIRST_FRAME_RENDERED;
    firstFrameState = C.FIRST_FRAME_RENDERED;
    lastReleaseRealtimeUs = msToUs(clock.elapsedRealtime());
    return firstFrame;
  }

  /** Sets the clock that will be used. */
  public void setClock(Clock clock) {
    this.clock = clock;
  }

  /**
   * Allows the frame control to indicate the first frame can be released before this instance is
   * started.
   */
  public void allowReleaseFirstFrameBeforeStarted() {
    if (firstFrameState == C.FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED) {
      firstFrameState = C.FIRST_FRAME_NOT_RENDERED;
    }
  }

  /**
   * Whether the release control is ready to start playback.
   *
   * @see Renderer#isReady()
   * @param rendererReady Whether the renderer is ready.
   * @return Whether the release control is ready.
   */
  public boolean isReady(boolean rendererReady) {
    if (rendererReady && firstFrameState == C.FIRST_FRAME_RENDERED) {
      // Ready. If we were joining then we've now joined, so clear the joining deadline.
      joiningDeadlineMs = C.TIME_UNSET;
      return true;
    } else if (joiningDeadlineMs == C.TIME_UNSET) {
      // Not joining.
      return false;
    } else if (clock.elapsedRealtime() < joiningDeadlineMs) {
      // Joining and still withing the deadline.
      return true;
    } else {
      // The joining deadline has been exceeded. Give up and clear the deadline.
      joiningDeadlineMs = C.TIME_UNSET;
      return false;
    }
  }

  /** Joins the release control to a new stream. */
  public void join() {
    joiningDeadlineMs =
        allowedJoiningTimeMs > 0 ? (clock.elapsedRealtime() + allowedJoiningTimeMs) : C.TIME_UNSET;
  }

  /**
   * Returns a {@link FrameReleaseAction} for a video frame which instructs a renderer what to do
   * with the frame.
   *
   * @param presentationTimeUs The presentation time of the video frame, in microseconds.
   * @param positionUs The current playback position, in microseconds.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     taken approximately at the time the playback position was {@code positionUs}.
   * @param outputStreamStartPositionUs The stream's start position, in microseconds.
   * @param isLastFrame Whether the frame is known to contain the last frame of the current stream.
   * @param frameReleaseInfo A {@link FrameReleaseInfo} that will be filled with detailed data only
   *     if the method returns {@link #FRAME_RELEASE_IMMEDIATELY} or {@link
   *     #FRAME_RELEASE_SCHEDULED}.
   * @return A {@link FrameReleaseAction} that should instruct the renderer whether to release the
   *     frame or not.
   */
  public @FrameReleaseAction int getFrameReleaseAction(
      long presentationTimeUs,
      long positionUs,
      long elapsedRealtimeUs,
      long outputStreamStartPositionUs,
      boolean isLastFrame,
      FrameReleaseInfo frameReleaseInfo) {
    frameReleaseInfo.reset();

    if (initialPositionUs == C.TIME_UNSET) {
      initialPositionUs = positionUs;
    }
    if (lastPresentationTimeUs != presentationTimeUs) {
      frameReleaseHelper.onNextFrame(presentationTimeUs);
      lastPresentationTimeUs = presentationTimeUs;
    }

    frameReleaseInfo.earlyUs =
        calculateEarlyTimeUs(positionUs, elapsedRealtimeUs, presentationTimeUs);

    if (shouldForceRelease(positionUs, frameReleaseInfo.earlyUs, outputStreamStartPositionUs)) {
      return FRAME_RELEASE_IMMEDIATELY;
    }
    if (!started
        || positionUs == initialPositionUs
        || frameReleaseInfo.earlyUs > MAX_EARLY_US_THRESHOLD) {
      return FRAME_RELEASE_TRY_AGAIN_LATER;
    }

    // Calculate release time and and adjust earlyUs to screen vsync.
    long systemTimeNs = clock.nanoTime();
    frameReleaseInfo.releaseTimeNs =
        frameReleaseHelper.adjustReleaseTime(systemTimeNs + (frameReleaseInfo.earlyUs * 1_000));
    frameReleaseInfo.earlyUs = (frameReleaseInfo.releaseTimeNs - systemTimeNs) / 1_000;
    // While joining, late frames are skipped.
    boolean treatDropAsSkip = joiningDeadlineMs != C.TIME_UNSET;
    if (frameTimingEvaluator.shouldDropFramesToKeyframe(
        frameReleaseInfo.earlyUs, elapsedRealtimeUs, isLastFrame)) {
      return treatDropAsSkip ? FRAME_RELEASE_SKIP_TO_KEYFRAME : FRAME_RELEASE_DROP_TO_KEYFRAME;
    } else if (frameTimingEvaluator.shouldDropFrame(
        frameReleaseInfo.earlyUs, elapsedRealtimeUs, isLastFrame)) {
      // While joining, dropped buffers are considered skipped.
      return treatDropAsSkip ? FRAME_RELEASE_SKIP : FRAME_RELEASE_DROP;
    }
    return FRAME_RELEASE_SCHEDULED;
  }

  /** Resets the release control. */
  public void reset() {
    frameReleaseHelper.onPositionReset();
    lastPresentationTimeUs = C.TIME_UNSET;
    initialPositionUs = C.TIME_UNSET;
    lowerFirstFrameState(C.FIRST_FRAME_NOT_RENDERED);
    joiningDeadlineMs = C.TIME_UNSET;
  }

  /**
   * Change the {@link C.VideoChangeFrameRateStrategy}, used when calling {@link
   * Surface#setFrameRate}.
   */
  public void setChangeFrameRateStrategy(
      @C.VideoChangeFrameRateStrategy int changeFrameRateStrategy) {
    frameReleaseHelper.setChangeFrameRateStrategy(changeFrameRateStrategy);
  }

  /** Sets the playback speed. Called when the renderer playback speed changes. */
  public void setPlaybackSpeed(float speed) {
    this.playbackSpeed = speed;
    frameReleaseHelper.onPlaybackSpeed(speed);
  }

  private void lowerFirstFrameState(@C.FirstFrameState int firstFrameState) {
    this.firstFrameState = min(this.firstFrameState, firstFrameState);
  }

  /**
   * Calculates the time interval between the current player position and the frame presentation
   * time.
   *
   * @param positionUs The current media time in microseconds, measured at the start of the current
   *     iteration of the rendering loop.
   * @param elapsedRealtimeUs {@link SystemClock#elapsedRealtime()} in microseconds, measured at the
   *     start of the current iteration of the rendering loop.
   * @param framePresentationTimeUs The presentation time of the frame in microseconds.
   * @return The calculated early time, in microseconds.
   */
  private long calculateEarlyTimeUs(
      long positionUs, long elapsedRealtimeUs, long framePresentationTimeUs) {
    // Calculate how early we are. In other words, the realtime duration that needs to elapse whilst
    // the renderer is started before the frame should be rendered. A negative value means that
    // we're already late.
    // Note: Use of double rather than float is intentional for accuracy in the calculations below.
    long earlyUs = (long) ((framePresentationTimeUs - positionUs) / (double) playbackSpeed);
    if (started) {
      // Account for the elapsed time since the start of this iteration of the rendering loop.
      earlyUs -= Util.msToUs(clock.elapsedRealtime()) - elapsedRealtimeUs;
    }

    return earlyUs;
  }

  /** Returns whether a frame should be force released. */
  private boolean shouldForceRelease(
      long positionUs, long earlyUs, long outputStreamStartPositionUs) {
    if (joiningDeadlineMs != C.TIME_UNSET) {
      // No force releasing during joining.
      return false;
    }
    switch (firstFrameState) {
      case C.FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED:
        return started;
      case C.FIRST_FRAME_NOT_RENDERED:
        return true;
      case C.FIRST_FRAME_NOT_RENDERED_AFTER_STREAM_CHANGE:
        return positionUs >= outputStreamStartPositionUs;
      case C.FIRST_FRAME_RENDERED:
        long elapsedTimeSinceLastReleaseUs =
            msToUs(clock.elapsedRealtime()) - lastReleaseRealtimeUs;
        return started
            && frameTimingEvaluator.shouldForceReleaseFrame(earlyUs, elapsedTimeSinceLastReleaseUs);
      default:
        throw new IllegalStateException();
    }
  }
}
