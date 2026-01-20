/*
 * Copyright 2026 Proify, Tomakino
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
package io.github.proify.lyricon.lyric.model.interfaces

/**
 * 歌词时间
 *
 * @property begin 开始时间
 * @property end 结束时间
 * @property duration 持续时间
 */
interface ILyricTiming {
    var begin: Long
    var end: Long
    var duration: Long
}