/*
 * Copyright 2022 Hossein Naderi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.k8s.apimachinery.pkg.runtime

import dev.hnaderi.k8s.utils._

/** RawExtension is used to hold extensions in external versions.
  *
  * To use this, make a field which has RawExtension as its type in your
  * external, versioned struct, and Object in your internal struct. You also
  * need to register your various plugin types.
  *
  * // Internal package:
  *
  * type MyAPIObject struct { runtime.TypeMeta `json:",inline"` MyPlugin
  * runtime.Object `json:"myPlugin"` }
  *
  * type PluginA struct { AOption string `json:"aOption"` }
  *
  * // External package:
  *
  * type MyAPIObject struct { runtime.TypeMeta `json:",inline"` MyPlugin
  * runtime.RawExtension `json:"myPlugin"` }
  *
  * type PluginA struct { AOption string `json:"aOption"` }
  *
  * // On the wire, the JSON will look something like this:
  *
  * { "kind":"MyAPIObject", "apiVersion":"v1", "myPlugin": { "kind":"PluginA",
  * "aOption":"foo", }, }
  *
  * So what happens? Decode first uses json or yaml to unmarshal the serialized
  * data into your external MyAPIObject. That causes the raw JSON to be stored,
  * but not unpacked. The next step is to copy (using pkg/conversion) into the
  * internal struct. The runtime package's DefaultScheme has conversion
  * functions installed which will unpack the JSON stored in RawExtension,
  * turning it into the correct object type, and storing it in the Object.
  * (TODO: In the case where the object is of an unknown type, a runtime.Unknown
  * object will be created and stored.)
  */
final case class RawExtension()
//TODO

object RawExtension {
  implicit def encoder[T](implicit
      builder: Builder[T]
  ): Encoder[RawExtension, T] = new Encoder[RawExtension, T] {
    def apply(r: RawExtension): T = builder.ofFields()
  }
}
