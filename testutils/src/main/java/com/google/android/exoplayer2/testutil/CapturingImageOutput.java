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
package com.google.android.exoplayer2.testutil;

import android.graphics.Bitmap;
import com.google.android.exoplayer2.ext.image.ImageOutput;
import com.google.android.exoplayer2.testutil.Dumper.Dumpable;
import java.util.ArrayList;
import java.util.List;

/** A {@link ImageOutput} that captures image availability events. */
public final class CapturingImageOutput implements Dumpable, ImageOutput {

  private final List<Dumpable> renderedBitmaps;

  private int imageCount;

  public CapturingImageOutput() {
    renderedBitmaps = new ArrayList<>();
  }

  @Override
  public void onImageAvailable(long presentationTimeUs, Bitmap bitmap) {
    imageCount++;
    renderedBitmaps.add(
        dumper -> {
          dumper.startBlock("image output #" + imageCount);
          dumper.add("presentationTimeUs", presentationTimeUs);
          dumper.endBlock();
        });
  }

  @Override
  public void dump(Dumper dumper) {
    dumper.add("rendered image count", imageCount);
    for (Dumpable dumpable : renderedBitmaps) {
      dumpable.dump(dumper);
    }
  }
}
