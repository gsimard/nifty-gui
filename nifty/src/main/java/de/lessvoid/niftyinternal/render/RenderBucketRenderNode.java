/*
 * Copyright (c) 2015, Nifty GUI Community
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.lessvoid.niftyinternal.render;

import de.lessvoid.nifty.NiftyCanvas;
import de.lessvoid.nifty.spi.NiftyRenderDevice;
import de.lessvoid.nifty.spi.node.NiftyNodeContentImpl;
import de.lessvoid.nifty.types.*;
import de.lessvoid.niftyinternal.accessor.NiftyCanvasAccessor;
import de.lessvoid.niftyinternal.canvas.Command;
import de.lessvoid.niftyinternal.canvas.Context;
import de.lessvoid.niftyinternal.canvas.InternalNiftyCanvas;
import de.lessvoid.niftyinternal.math.Mat4;
import de.lessvoid.niftyinternal.math.Vec4;
import de.lessvoid.niftyinternal.render.batch.BatchManager;

import java.util.List;

/**
 * A RenderBucketRenderNode is the RenderNode representation of a NiftyContentNode. It stores the Context (texture) as
 * well as the NiftyCanvas for the NiftyContentNode.
 *
 * Created by void on 13.09.15.
 */
public class RenderBucketRenderNode implements Comparable<RenderBucketRenderNode> {
  private final NiftyCanvas niftyCanvas = NiftyCanvasAccessor.getDefault().newNiftyCanvas();

  private int width;
  private int height;
  private Mat4 localToScreen;
  private Context context;
  private int renderOrder;

  public RenderBucketRenderNode(
      final int width,
      final int height,
      final Mat4 localToScreen,
      final NiftyRenderDevice renderDevice) {
    this.width = width;
    this.height = height;
    this.localToScreen = localToScreen;
    this.context = createContext(renderDevice);
  }

  public void updateRenderOrder(final int renderOrder) {
    this.renderOrder = renderOrder;
  }

  public void updateCanvas(final NiftyNodeContentImpl child) {
    NiftyCanvasAccessor.getDefault().getInternalNiftyCanvas(niftyCanvas).reset();
    child.updateCanvas(niftyCanvas);
  }

  public void updateContent(final int width, final int height, final Mat4 localToScreen, final NiftyRenderDevice renderDevice) {
    InternalNiftyCanvas canvas = NiftyCanvasAccessor.getDefault().getInternalNiftyCanvas(niftyCanvas);

    boolean canvasChanged = canvas.isChanged();
    boolean sizeChanged = updateSize(width, height, renderDevice);
    boolean transformationChanged = updateTransformation(localToScreen);

    if (canvasChanged || sizeChanged || transformationChanged) {
      context.bind(renderDevice, new BatchManager());
      context.prepare();

      List<Command> commands = canvas.getCommands();
      for (int i=0; i<commands.size(); i++) {
        Command command = commands.get(i);
        command.execute(context);
      }

      context.flush();
    }
  }

  public void render(final BatchManager batchManager, final Mat4 bucketTransformation) {
    Mat4 local = Mat4.mul(bucketTransformation, localToScreen);
    batchManager.addChangeCompositeOperation(NiftyCompositeOperation.SourceOver);
    batchManager.addTextureQuad(context.getNiftyTexture(), local, NiftyColor.white());
  }

  public NiftyRect getScreenSpaceAABB() {
    Vec4 p0 = Mat4.transform(localToScreen, new Vec4(  0.f,    0.f, 0.f, 1.f));
    Vec4 p1 = Mat4.transform(localToScreen, new Vec4(width,    0.f, 0.f, 1.f));
    Vec4 p2 = Mat4.transform(localToScreen, new Vec4(width, height, 0.f, 1.f));
    Vec4 p3 = Mat4.transform(localToScreen, new Vec4(  0.f, height, 0.f, 1.f));
    float minX = Math.min(Math.min(p0.getX(), p1.getX()), Math.min(p2.getX(), p3.getX()));
    float maxX = Math.max(Math.max(p0.getX(), p1.getX()), Math.max(p2.getX(), p3.getX()));
    float minY = Math.min(Math.min(p0.getY(), p1.getY()), Math.min(p2.getY(), p3.getY()));
    float maxY = Math.max(Math.max(p0.getY(), p1.getY()), Math.max(p2.getY(), p3.getY()));
    return NiftyRect.newNiftyRect(
        NiftyPoint.newNiftyPoint(minX, minY),
        NiftySize.newNiftySize(maxX - minX, maxY - minY));
  }

  @Override
  public int compareTo(final RenderBucketRenderNode o) {
    return Integer.valueOf(this.renderOrder).compareTo(o.renderOrder);
  }

  private boolean updateSize(
      final int newWidth,
      final int newHeight,
      final NiftyRenderDevice renderDevice) {
    if (newWidth == width && newHeight == height) {
      return false;
    }

    width = newWidth;
    height = newHeight;

    context.free();
    context = createContext(renderDevice);
    return true;
  }

  private boolean updateTransformation(final Mat4 newLocalToScreen) {
    if (newLocalToScreen.compare(localToScreen)) {
      return false;
    }
    localToScreen = newLocalToScreen;
    return true;
  }

  private Context createContext(final NiftyRenderDevice renderDevice) {
    return new Context(
        renderDevice.createTexture(width, height, NiftyRenderDevice.FilterMode.Linear),
        renderDevice.createTexture(width, height, NiftyRenderDevice.FilterMode.Linear));
  }
}