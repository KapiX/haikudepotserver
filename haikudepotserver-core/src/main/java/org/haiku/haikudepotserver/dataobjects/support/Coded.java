/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects.support;

import org.haiku.haikudepotserver.dataobjects.PkgUrlType;

/**
 * <p>This interface defines a method to get a code.  This is used mostly on reference data such as
 * {@link PkgUrlType} for example where the code provides a
 * machine-reference (not human readable) identification mechanism for an instance of an object.</p>
 */

public interface Coded {

    String getCode();

}
