/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.model;

import com.google.common.base.Preconditions;
import org.haiku.pkg.AttributeContext;
import org.haiku.pkg.HpkException;
import org.haiku.pkg.heap.HeapCoordinates;

/**
 * <p>This type of attribute refers to raw data.  It uses coordinates into the heap to provide a source for the
 * data.</p>
 */

public class RawHeapAttribute extends RawAttribute {

    private HeapCoordinates heapCoordinates;

    public RawHeapAttribute(AttributeId attributeId, HeapCoordinates heapCoordinates) {
        super(attributeId);
        Preconditions.checkNotNull(heapCoordinates);
        this.heapCoordinates = heapCoordinates;
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RawHeapAttribute that = (RawHeapAttribute) o;

        if (!heapCoordinates.equals(that.heapCoordinates)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return heapCoordinates.hashCode();
    }

    @Override
    public byte[] getValue(AttributeContext context) {
        byte[] buffer = new byte[(int) heapCoordinates.getLength()];
        context.getHeapReader().readHeap(buffer, 0, heapCoordinates);
        return buffer;
    }

    @Override
    public AttributeType getAttributeType() {
        return AttributeType.RAW;
    }

    @Override
    public String toString() {
        return String.format("%s : @%s",super.toString(),heapCoordinates.toString());
    }

}
