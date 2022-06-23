/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.primitivio

import cc.redberry.pipe.CUtils
import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.blocks.FilteringPort
import cc.redberry.pipe.util.FlatteningOutputPort

inline fun <reified T : Any> PrimitivI.readObjectOptional(): T? = readObject(T::class.java)
inline fun <reified T : Any> PrimitivI.readObjectRequired(): T = readObject(T::class.java)

inline fun <reified K : Any, reified V : Any> PrimitivI.readMap(): Map<K, V> =
    Util.readMap(this, K::class.java, V::class.java)

inline fun <reified T : Any> PrimitivI.readList(): List<T> = Util.readList(T::class.java, this)

fun <T, R> OutputPort<T>.map(function: (T) -> R): OutputPort<R> = CUtils.wrap(this, function)

fun <T, R> OutputPort<T>.mapNotNull(function: (T) -> R?): OutputPort<R> = flatMap {
    listOfNotNull(function(it))
}

fun <T> List<OutputPort<T>>.flatten(): OutputPort<T> =
    FlatteningOutputPort(CUtils.asOutputPort(this))

fun <T, R> OutputPort<T>.flatMap(function: (element: T) -> Iterable<R>): OutputPort<R> =
    FlatteningOutputPort(CUtils.wrap(this) {
        CUtils.asOutputPort(function(it))
    })

fun <T> OutputPort<T>.filter(test: (element: T) -> Boolean): OutputPort<T> =
    FilteringPort(this, test)

fun <T> OutputPort<T>.forEach(action: (element: T) -> Unit): Unit =
    CUtils.it(this).forEach(action)

fun <T> OutputPort<T>.toList(): List<T> =
    CUtils.it(this).toList()

fun <T> OutputPort<T>.count(): Int =
    CUtils.it(this).count()

