package io.github.nexfusion.nexfusion.extension.spotify

import kotlin.test.Test
import kotlin.test.assertEquals

class InflatedAdLayoutTest {
    @Test
    fun detachedInflationHidesTheReturnedView() {
        assertEquals(InflatedAdLayout.RETURNED_VIEW, InflatedAdLayout.indexOfInflatedView(false, false, 0))
    }

    @Test
    fun detachedInflationIgnoresTheRootChildren() {
        assertEquals(InflatedAdLayout.RETURNED_VIEW, InflatedAdLayout.indexOfInflatedView(false, false, 3))
    }

    @Test
    fun attachedInflationWithoutRootHidesTheReturnedView() {
        // LayoutInflater returns the inflated view itself when there is no root to attach to.
        assertEquals(InflatedAdLayout.RETURNED_VIEW, InflatedAdLayout.indexOfInflatedView(true, false, 0))
    }

    @Test
    fun attachedInflationHidesTheLastChildOfTheRoot() {
        // The inflated view is appended to the root, which is returned instead of it.
        assertEquals(2, InflatedAdLayout.indexOfInflatedView(true, true, 3))
    }

    @Test
    fun attachedInflationIntoAnEmptyRootHidesNothing() {
        assertEquals(InflatedAdLayout.NONE, InflatedAdLayout.indexOfInflatedView(true, true, 0))
    }
}
