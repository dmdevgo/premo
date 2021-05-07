/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2021 Dmitriy Gorbunov (dmitriy.goto@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.dmdev.premo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class Action<T>(scope: CoroutineScope) {

//    private val channel = Channel<T>(
//        capacity = Channel.RENDEZVOUS,
//        onBufferOverflow = BufferOverflow.SUSPEND
//    )
//    private val inFlow = channel.consumeAsFlow()
    private val inFlow = MutableSharedFlow<T>(extraBufferCapacity = 1) // Buffer here is just a workaround to use tryEmit

    private val outFlow = MutableSharedFlow<T>()

    private var emissionInProgress: Boolean = false

    init {
        scope.launch {
            inFlow
                .collect {
                    emissionInProgress = true
                    outFlow.emit(it)
                    emissionInProgress = false
                }
        }
    }

    fun flow(): Flow<T> = outFlow.filter { emissionInProgress } // Filter that came after suspending

    operator fun invoke(value: T) {
//        channel.offer(value)
        if (!emissionInProgress) inFlow.tryEmit(value)
    }
}

operator fun Action<Unit>.invoke() {
    this.invoke(Unit)
}

@Suppress("FunctionName")
fun <T> PresentationModel.ActionChain(
    actionChain: (Flow<T>.() -> Flow<*>)
): Action<T> {

    val action = Action<T>(pmScope)

    actionChain
        .invoke(action.flow())
        .launchIn(pmScope)

    return action
}

@Suppress("FunctionName")
fun <T> PresentationModel.Action(
    doAction: suspend PresentationModel.(value: T) -> Unit
): Action<T> {

    val action = Action<T>(pmScope)

    action.flow()
        .onEach { doAction(it) }
        .launchIn(pmScope)

    return action
}