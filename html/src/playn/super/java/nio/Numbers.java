/*
 * Copyright 2010 Google Inc.
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
package java.nio;

import elemental2.core.Float32Array;
import elemental2.core.Float64Array;
import elemental2.core.Int32Array;
import elemental2.core.Int8Array;

class Numbers {
        static Int8Array wba = new Int8Array(8);
        static Int32Array wia = new Int32Array(wba.buffer, 0, 2);
        static Float32Array wfa = new Float32Array(wba.buffer, 0, 2);
        static Float64Array wda = new Float64Array(wba.buffer, 0, 1);

        public static final int floatToIntBits(float f) {
                wfa.setAt(0, (double) f);
                return (int)(double)wia.getAt(0);
        }

        public static final float intBitsToFloat(int i) {
                wia.setAt(0, (double) i);
                return (float)(double)wfa.getAt(0);
        }

        public static final double longBitsToDouble(long i) {
          throw new RuntimeException("longBitsToDouble NYI");
        }

        public static final long doubleToRawLongBits(double i) {
          throw new RuntimeException("doubleToRawLongBits NYI");
        }

        // TODO(jgw): Ugly hack to avoid longs.
        public static final void setDouble(double d) {
          wda.setAt(0, d);
        }

        public static final double getDouble() {
          return wda.getAt(0);
        }

        public static final int getLoInt() {
          return (int)(double)wia.getAt(0);
        }

        public static final int getHiInt() {
          return (int)(double)wia.getAt(1);
        }

        public static final void setLoInt(int i) {
          wia.setAt(0, (double)i);
        }

        public static final void setHiInt(int i) {
          wia.setAt(1, (double)i);
        }
}
