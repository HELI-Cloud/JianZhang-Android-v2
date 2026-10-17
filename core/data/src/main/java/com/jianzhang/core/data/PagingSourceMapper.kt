package com.jianzhang.core.data

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * Paging 3 only maps [androidx.paging.PagingData], not a PagingSource itself.
 * This wrapper keeps Room's invalidation semantics while translating database
 * rows into domain objects at the repository boundary.
 */
fun <Key : Any, Value : Any, ToValue : Any> PagingSource<Key, Value>.mapItems(
    transform: suspend (Value) -> ToValue,
): PagingSource<Key, ToValue> = MappingPagingSource(this, transform)

private class MappingPagingSource<Key : Any, Value : Any, ToValue : Any>(
    private val delegate: PagingSource<Key, Value>,
    private val transform: suspend (Value) -> ToValue,
) : PagingSource<Key, ToValue>() {
    init {
        delegate.registerInvalidatedCallback { invalidate() }
    }

    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, ToValue> =
        when (val result = delegate.load(params)) {
            is LoadResult.Page -> LoadResult.Page(
                data = result.data.map { transform(it) },
                prevKey = result.prevKey,
                nextKey = result.nextKey,
                itemsBefore = result.itemsBefore,
                itemsAfter = result.itemsAfter,
            )
            is LoadResult.Error -> LoadResult.Error<Key, ToValue>(result.throwable)
            is LoadResult.Invalid -> LoadResult.Invalid()
        }

    override fun getRefreshKey(state: PagingState<Key, ToValue>): Key? {
        val anchor = state.anchorPosition ?: return state.pages.lastOrNull()?.nextKey
        var start = 0
        for (page in state.pages) {
            val end = start + page.data.size
            if (anchor < end) return page.prevKey ?: page.nextKey
            start = end
        }
        return state.pages.lastOrNull()?.nextKey
    }
}
