/**
 * Copyright 2011 The PlayN Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package playn.html;

import com.google.gwt.dom.client.CanvasElement;
import com.google.gwt.dom.client.ImageElement;
import com.google.gwt.typedarrays.client.Float32Array;
import com.google.gwt.typedarrays.client.Int32Array;
import com.google.gwt.typedarrays.client.Uint16Array;
import com.google.gwt.typedarrays.client.Uint8Array;
import com.google.gwt.webgl.client.WebGLBuffer;
import com.google.gwt.webgl.client.WebGLFramebuffer;
import com.google.gwt.webgl.client.WebGLProgram;
import com.google.gwt.webgl.client.WebGLRenderingContext;
import com.google.gwt.webgl.client.WebGLTexture;
import com.google.gwt.webgl.client.WebGLUniformLocation;
import com.google.gwt.webgl.client.WebGLUtil;

import static com.google.gwt.webgl.client.WebGLRenderingContext.*;

import playn.core.InternalTransform;
import playn.core.gl.GLContext;
import playn.core.gl.LayerGL;

/**
 * Implements {@link GLContext} via WebGL.
 */
public class HtmlGLContext extends GLContext
{
  private static final int VERTEX_SIZE = 10;              // 10 floats per vertex

// TODO(jgw): Re-enable longer element buffers once we figure out why they're causing weird
// performance degradation.
//  private static final int MAX_VERTS = 400;               // 100 quads
//  private static final int MAX_ELEMS = MAX_VERTS * 6 / 4; // At most 6 verts per quad

// These values allow only one quad at a time (there's no generalized polygon rendering available
// in Surface yet that would use more than 4 points / 2 triangles).
  private static final int MAX_VERTS = 4;
  private static final int MAX_ELEMS = 6;

  private class Shader {
    WebGLProgram program;
    WebGLUniformLocation uScreenSizeLoc;
    int aMatrix, aTranslation, aPosition, aTexture;

    WebGLBuffer vertexBuffer, elementBuffer;

    Float32Array vertexData = Float32Array.create(VERTEX_SIZE * MAX_VERTS);
    Uint16Array elementData = Uint16Array.create(MAX_ELEMS);
    int vertexOffset, elementOffset;

    Shader(String fragmentShader) {
      // Compile the shader.
      String vertexShader = Shaders.INSTANCE.vertexShader().getText();
      program = WebGLUtil.createShaderProgram(gl, vertexShader, fragmentShader);

      // glGet*() calls are slow; determine locations once.
      uScreenSizeLoc = gl.getUniformLocation(program, "u_ScreenSize");
      aMatrix = gl.getAttribLocation(program, "a_Matrix");
      aTranslation = gl.getAttribLocation(program, "a_Translation");
      aPosition = gl.getAttribLocation(program, "a_Position");
      aTexture = gl.getAttribLocation(program, "a_Texture");

      // Create the vertex and index buffers.
      vertexBuffer = gl.createBuffer();
      elementBuffer = gl.createBuffer();
    }

    boolean prepare() {
      if (useShader(this)) {
        gl.useProgram(program);
        gl.uniform2fv(uScreenSizeLoc, new float[] { screenWidth, screenHeight });
        gl.bindBuffer(ARRAY_BUFFER, vertexBuffer);
        gl.bindBuffer(ELEMENT_ARRAY_BUFFER, elementBuffer);

        gl.enableVertexAttribArray(aMatrix);
        gl.enableVertexAttribArray(aTranslation);
        gl.enableVertexAttribArray(aPosition);
        if (aTexture != -1) {
          gl.enableVertexAttribArray(aTexture);
        }

        gl.vertexAttribPointer(aMatrix, 4, FLOAT, false, 40, 0);
        gl.vertexAttribPointer(aTranslation, 2, FLOAT, false, 40, 16);
        gl.vertexAttribPointer(aPosition, 2, FLOAT, false, 40, 24);
        if (aTexture != -1) {
          gl.vertexAttribPointer(aTexture, 2, FLOAT, false, 40, 32);
        }

        return true;
      }
      return false;
    }

    void flush() {
      if (vertexOffset == 0) {
        return;
      }

      // TODO(jgw): Change this back. It only works because we've limited MAX_VERTS, which only
      // works because there are no >4 vertex draws happening.
      // gl.bufferData(ARRAY_BUFFER, vertexData.subarray(0, vertexOffset), STREAM_DRAW);
      // gl.bufferData(ELEMENT_ARRAY_BUFFER, elementData.subarray(0, elementOffset), STREAM_DRAW);
      gl.bufferData(ARRAY_BUFFER, vertexData, STREAM_DRAW);
      gl.bufferData(ELEMENT_ARRAY_BUFFER, elementData, STREAM_DRAW);

      gl.drawElements(TRIANGLES, elementOffset, UNSIGNED_SHORT, 0);
      vertexOffset = elementOffset = 0;
    }

    int beginPrimitive(int vertexCount, int elemCount) {
      int vertIdx = vertexOffset / VERTEX_SIZE;
      if ((vertIdx + vertexCount > MAX_VERTS) || (elementOffset + elemCount > MAX_ELEMS)) {
        flush();
        return 0;
      }
      return vertIdx;
    }

    void buildVertex(InternalTransform local, float dx, float dy) {
      buildVertex(local, dx, dy, 0, 0);
    }

    void buildVertex(InternalTransform local, float dx, float dy, float sx, float sy) {
      vertexData.set(((HtmlInternalTransform)local).matrix(), vertexOffset);
      vertexData.set(vertexOffset + 6, dx);
      vertexData.set(vertexOffset + 7, dy);
      vertexData.set(vertexOffset + 8, sx);
      vertexData.set(vertexOffset + 9, sy);
      vertexOffset += VERTEX_SIZE;
    }

    void addElement(int index) {
      elementData.set(elementOffset++, index);
    }
  }

  private class TextureShader extends Shader {
    WebGLUniformLocation uTexture;
    WebGLUniformLocation uAlpha;
    WebGLTexture lastTex;
    float lastAlpha;

    TextureShader() {
      super(Shaders.INSTANCE.texFragmentShader().getText());
      uTexture = gl.getUniformLocation(program, "u_Texture");
      uAlpha = gl.getUniformLocation(program, "u_Alpha");
    }

    @Override
    void flush() {
      gl.bindTexture(TEXTURE_2D, lastTex);
      super.flush();
    }

    void prepare(WebGLTexture tex, float alpha) {
      if (super.prepare()) {
        gl.activeTexture(TEXTURE0);
        gl.uniform1i(uTexture, 0);
      }

      if (tex == lastTex && alpha == lastAlpha) {
        return;
      }
      flush();

      gl.uniform1f(uAlpha, alpha);
      lastAlpha = alpha;
      lastTex = tex;
    }
  }

  private class ColorShader extends Shader {
    WebGLUniformLocation uColor;
    WebGLUniformLocation uAlpha;
    Float32Array colors = Float32Array.create(4);
    int lastColor;
    float lastAlpha;

    ColorShader() {
      super(Shaders.INSTANCE.colorFragmentShader().getText());
      uColor = gl.getUniformLocation(program, "u_Color");
      uAlpha = gl.getUniformLocation(program, "u_Alpha");
    }

    void prepare(int color, float alpha) {
      super.prepare();

      if (color == lastColor && alpha == lastAlpha) {
        return;
      }
      flush();

      gl.uniform1f(uAlpha, alpha);
      lastAlpha = alpha;
      setColor(color);
    }

    private void setColor(int color) {
      // ABGR.
      colors.set(3, (float)((color >> 24) & 0xff) / 255);
      colors.set(0, (float)((color >> 16) & 0xff) / 255);
      colors.set(1, (float)((color >> 8) & 0xff) / 255);
      colors.set(2, (float)((color >> 0) & 0xff) / 255);
      gl.uniform4fv(uColor, colors);

      lastColor = color;
    }
  }

  public final WebGLRenderingContext gl;

  private final CanvasElement canvas;
  private final TextureShader texShader;
  private final ColorShader colorShader;

  private WebGLFramebuffer lastFBuf;
  private int screenWidth, screenHeight;

  // Shaders & Meshes.
  private Shader curShader;

  // Debug counters.
  // private int texCount;

  HtmlGLContext(CanvasElement canvas) throws RuntimeException {
    this.canvas = canvas;

    // Try to create a context. If this returns null, then the browser doesn't support WebGL on
    // this machine.
    this.gl = WebGLRenderingContext.getContext(canvas, null);
    // Some systems seem to have a problem where they return a valid context, but it's in an error
    // static initially. We give up and fall back to dom/canvas in this case, because nothing seems
    // to work properly.
    if (gl == null || gl.getError() != NO_ERROR) {
      throw new RuntimeException("GL context not created [err=" +
                                 (gl == null ? "null" : gl.getError()) + "]");
    }

    gl.disable(CULL_FACE);
    gl.enable(BLEND);
    gl.blendEquation(FUNC_ADD);
    gl.blendFunc(ONE, ONE_MINUS_SRC_ALPHA);
    gl.pixelStorei(UNPACK_PREMULTIPLY_ALPHA_WEBGL, ONE);

    // try basic GL operations to detect failure cases early
    tryBasicGLCalls();

    this.texShader = new TextureShader();
    this.colorShader = new ColorShader();
  }

  void paint(LayerGL rootLayer) {
    bindFramebuffer();

    // Clear to transparent.
    clear(0, 0, 0, 0);

    // Paint all the layers.
    rootLayer.paint(HtmlInternalTransform.IDENTITY, 1);

    // Guarantee a flush.
    useShader(null);
  }

  @Override
  public Object createFramebuffer(Object tex) {
    WebGLFramebuffer fbuf = gl.createFramebuffer();
    gl.bindFramebuffer(FRAMEBUFFER, fbuf);
    gl.framebufferTexture2D(FRAMEBUFFER, COLOR_ATTACHMENT0, TEXTURE_2D, (WebGLTexture) tex, 0);
    return fbuf;
  }

  @Override
  public void bindFramebuffer(Object fbuf, int width, int height) {
    bindFramebuffer((WebGLFramebuffer) fbuf, width, height, false);
  }

  @Override
  public void deleteFramebuffer(Object fbuf) {
    gl.deleteFramebuffer((WebGLFramebuffer) fbuf);
  }

  void bindFramebuffer() {
    bindFramebuffer(null, canvas.getWidth(), canvas.getHeight(), false);
  }

  void bindFramebuffer(WebGLFramebuffer fbuf, int width, int height, boolean force) {
    if (force || lastFBuf != fbuf) {
      flush();

      lastFBuf = fbuf;
      gl.bindFramebuffer(FRAMEBUFFER, fbuf);
      gl.viewport(0, 0, width, height);
      screenWidth = width;
      screenHeight = height;
    }
  }

  @Override
  public WebGLTexture createTexture(boolean repeatX, boolean repeatY) {
    WebGLTexture tex = gl.createTexture();
    gl.bindTexture(TEXTURE_2D, tex);
    gl.texParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, LINEAR);
    gl.texParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, LINEAR);
    gl.texParameteri(TEXTURE_2D, TEXTURE_WRAP_S, repeatX ? REPEAT : CLAMP_TO_EDGE);
    gl.texParameteri(TEXTURE_2D, TEXTURE_WRAP_T, repeatY ? REPEAT : CLAMP_TO_EDGE);
    // ++texCount;
    return tex;
  }

  @Override
  public WebGLTexture createTexture(int width, int height, boolean repeatX, boolean repeatY) {
    WebGLTexture tex = createTexture(repeatX, repeatY);
    gl.texImage2D(TEXTURE_2D, 0, RGBA, width, height, 0, RGBA, UNSIGNED_BYTE, null);
    return tex;
  }


  @Override
  public void destroyTexture(Object tex) {
    gl.deleteTexture((WebGLTexture)tex);
    // --texCount;
  }

  void updateTexture(WebGLTexture tex, ImageElement img) {
    gl.bindTexture(TEXTURE_2D, tex);
    gl.texImage2D(TEXTURE_2D, 0, RGBA, RGBA, UNSIGNED_BYTE, img);
  }

  @Override
  public void drawTexture(Object otex, float texWidth, float texHeight, InternalTransform local,
                          float dx, float dy, float dw, float dh,
                          float sx, float sy, float sw, float sh, float alpha) {
    WebGLTexture tex = (WebGLTexture)otex;
    texShader.prepare(tex, alpha);

    sx /= texWidth;  sw /= texWidth;
    sy /= texHeight; sh /= texHeight;

    int idx = texShader.beginPrimitive(4, 6);
    texShader.buildVertex(local, dx,      dy,      sx,      sy);
    texShader.buildVertex(local, dx + dw, dy,      sx + sw, sy);
    texShader.buildVertex(local, dx,      dy + dh, sx,      sy + sh);
    texShader.buildVertex(local, dx + dw, dy + dh, sx + sw, sy + sh);

    texShader.addElement(idx + 0); texShader.addElement(idx + 1); texShader.addElement(idx + 2);
    texShader.addElement(idx + 1); texShader.addElement(idx + 3); texShader.addElement(idx + 2);
  }

  @Override
  public void fillRect(InternalTransform local, float dx, float dy, float dw, float dh,
                       float texWidth, float texHeight, Object otex, float alpha) {
    WebGLTexture tex = (WebGLTexture) otex;
    texShader.prepare(tex, alpha);

    float sx = dx / texWidth, sy = dy / texHeight;
    float sw = dw / texWidth, sh = dh / texHeight;

    int idx = texShader.beginPrimitive(4, 6);
    texShader.buildVertex(local, dx,      dy,      sx,      sy);
    texShader.buildVertex(local, dx + dw, dy,      sx + sw, sy);
    texShader.buildVertex(local, dx,      dy + dh, sx,      sy + sh);
    texShader.buildVertex(local, dx + dw, dy + dh, sx + sw, sy + sh);

    texShader.addElement(idx + 0); texShader.addElement(idx + 1); texShader.addElement(idx + 2);
    texShader.addElement(idx + 1); texShader.addElement(idx + 3); texShader.addElement(idx + 2);
  }

  @Override
  public void fillRect(InternalTransform local, float dx, float dy, float dw, float dh, int color,
                       float alpha) {
    colorShader.prepare(color, alpha);

    int idx = colorShader.beginPrimitive(4, 6);
    colorShader.buildVertex(local, dx,      dy);
    colorShader.buildVertex(local, dx + dw, dy);
    colorShader.buildVertex(local, dx,      dy + dh);
    colorShader.buildVertex(local, dx + dw, dy + dh);

    colorShader.addElement(idx + 0);
    colorShader.addElement(idx + 1);
    colorShader.addElement(idx + 2);
    colorShader.addElement(idx + 1);
    colorShader.addElement(idx + 3);
    colorShader.addElement(idx + 2);
  }

  @Override
  public void fillPoly(InternalTransform local, float[] positions, int color, float alpha) {
    colorShader.prepare(color, alpha);

    int idx = colorShader.beginPrimitive(4, 6);
    int points = positions.length / 2;
    for (int i = 0; i < points; ++i) {
      float dx = positions[i * 2];
      float dy = positions[i * 2 + 1];
      colorShader.buildVertex(local, dx, dy);
    }

    int a = idx + 0, b = idx + 1, c = idx + 2;
    int tris = points - 2;
    for (int i = 0; i < tris; ++i) {
      colorShader.addElement(a); colorShader.addElement(b); colorShader.addElement(c);
      a = c;
      b = a + 1;
      c = (i == tris - 2) ? idx : b + 1;
    }
  }

  @Override
  public void clear(float red, float green, float blue, float alpha) {
    gl.clearColor(red, green, blue, alpha);
    gl.clear(COLOR_BUFFER_BIT);
  }

  @Override
  public void flush() {
    if (curShader != null) {
      curShader.flush();
      curShader = null;
    }
  }

  @Override
  public InternalTransform createTransform() {
    return new HtmlInternalTransform();
  }

  private boolean useShader(Shader shader) {
    if (curShader != shader) {
      flush();
      curShader = shader;
      return true;
    }
    return false;
  }

  private void tryBasicGLCalls() throws RuntimeException {
    // test that our Float32 arrays work (a technique found in other WebGL checks)
    Float32Array testFloat32Array = Float32Array.create(new float[]{0.0f, 1.0f, 2.0f});
    if (testFloat32Array.get(0) != 0.0f || testFloat32Array.get(1) != 1.0f
        || testFloat32Array.get(2) != 2.0f) {
      throw new RuntimeException("Typed Float32Array check failed");
    }

    // test that our Int32 arrays work
    Int32Array testInt32Array = Int32Array.create(new int[]{0, 1, 2});
    if (testInt32Array.get(0) != 0 || testInt32Array.get(1) != 1 || testInt32Array.get(2) != 2) {
      throw new RuntimeException("Typed Int32Array check failed");
    }

    // test that our Uint16 arrays work
    Uint16Array testUint16Array = Uint16Array.create(new int[]{0, 1, 2});
    if (testUint16Array.get(0) != 0 || testUint16Array.get(1) != 1 ||
        testUint16Array.get(2) != 2) {
      throw new RuntimeException("Typed Uint16Array check failed");
    }

    // test that our Uint8 arrays work
    Uint8Array testUint8Array = Uint8Array.create(new int[]{0, 1, 2});
    if (testUint8Array.get(0) != 0 || testUint8Array.get(1) != 1 || testUint8Array.get(2) != 2) {
      throw new RuntimeException("Typed Uint8Array check failed");
    }

    // Perform GL read back test where we paint rgba(1, 1, 1, 1) and then read back that data.
    // (should be 100% opaque white).
    bindFramebuffer();
    clear(1, 1, 1, 1);
    int err = gl.getError();
    if (err != NO_ERROR) {
      throw new RuntimeException("Read back GL test failed to clear color (error " + err + ")");
    }
    Uint8Array pixelData = Uint8Array.create(4);
    gl.readPixels(0, 0, 1, 1, RGBA, UNSIGNED_BYTE, pixelData);
    if (pixelData.get(0) != 255 || pixelData.get(1) != 255 || pixelData.get(2) != 255) {
      throw new RuntimeException("Read back GL test failed to read back correct color");
    }
  }

  @SuppressWarnings("unused")
  private void printArray(String prefix, Float32Array arr, int length) {
    StringBuffer buf = new StringBuffer();
    buf.append(prefix + ": [");
    for (int i = 0; i < length; ++i) {
      buf.append(arr.get(i) + " ");
    }
    buf.append("]");
    System.out.println(buf);
  }

  @SuppressWarnings("unused")
  private void printArray(String prefix, Uint16Array arr, int length) {
    StringBuffer buf = new StringBuffer();
    buf.append(prefix + ": [");
    for (int i = 0; i < length; ++i) {
      buf.append(arr.get(i) + " ");
    }
    buf.append("]");
    System.out.println(buf);
  }
}