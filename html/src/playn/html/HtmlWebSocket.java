/**
 * Copyright 2010 The PlayN Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package playn.html;

import java.nio.ByteBuffer;

import com.google.gwt.typedarrays.shared.ArrayBuffer;
import com.google.gwt.typedarrays.shared.Uint8Array;
import com.google.gwt.typedarrays.shared.TypedArrays;

import elemental2.dom.Event;
import elemental2.dom.WebSocket;
import elemental2.dom.WebSocket.OncloseCallbackFn;
import elemental2.dom.WebSocket.OnmessageCallbackFn;
import elemental2.dom.WebSocket.OnopenCallbackFn;
import playn.core.Net;

public class HtmlWebSocket implements Net.WebSocket {

  private WebSocket ws;

  HtmlWebSocket(String url, final Listener listener) {
    ws = new WebSocket(url);
    ws.onopen = new OnopenCallbackFn() {
      @Override
      public Object onInvoke(Event event) {
        listener.onOpen();
        return null;
      }
    };
    ws.onmessage = new OnmessageCallbackFn() {
      @Override
      public Object onInvoke(elemental2.dom.MessageEvent<Object> event) {
        if (event.data instanceof String) {
          listener.onTextMessage((String) event.data);
        } else {
          listener.onDataMessage(TypedArrayHelper.wrap((ArrayBuffer) event.data));
        }
        return null;
      }
    };

    ws.onclose = new OncloseCallbackFn() {
      @Override
      public Object onInvoke(Event p0) {
        listener.onClose();
        return null;
      }
    };
  }

  @Override
  public void close() {
    ws.close();
  }

  @Override
  public void send(String data) {
    ws.send(data);
  }

  @Override
  public void send(ByteBuffer data) {
    int len = data.limit();
    // TODO(haustein) Sending the view directly does not work for some reason.
    // May be a chrome issue...?
    //  Object trick = data;
    // ArrayBufferView ta = ((HasArrayBufferView) trick).getTypedArray();
    // Int8Array view = Int8Array.create(ta.getBuffer(), ta.getByteOffset(), len)
    // ws.send(view);
    ArrayBuffer buf = TypedArrays.createArrayBuffer(len);
    Uint8Array view = TypedArrays.createUint8Array(buf);
    for (int i = 0; i < len; i++) {
      view.set(i, data.get(i));
    }
    ws.send((elemental2.core.ArrayBuffer)buf);
  }
}
