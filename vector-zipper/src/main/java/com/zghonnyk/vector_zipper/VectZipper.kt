package com.zghonnyk.vector_zipper

data class VectorZipper<E>(
    val index: Int,
    val list: List<E>
) {
    init {
        require(list.isNotEmpty()) { "Cannot be created from an empty list" }
        require(index in list.indices) { "Index is out of bounds" }
    }

    val focus: E
        get() = list[index]

    val isEmptyLefts: Boolean
        get() = index == 0

    val isEmptyRights: Boolean
        get() = index >= list.lastIndex

    fun goTo(targetIndex: Int): VectorZipper<E> =
        if (targetIndex in list.indices && targetIndex != index) copy(index = targetIndex) else this

    fun goForward(): VectorZipper<E> = if (!isEmptyRights) copy(index = index + 1) else this

    fun goBack(): VectorZipper<E> = if (!isEmptyLefts) copy(index = index - 1) else this
}