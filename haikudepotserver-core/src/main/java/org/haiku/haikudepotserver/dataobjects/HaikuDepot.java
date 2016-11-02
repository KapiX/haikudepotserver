/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import org.haiku.haikudepotserver.dataobjects.auto._HaikuDepot;

public class HaikuDepot extends _HaikuDepot {

    public enum CacheGroup {
        PKG_VERSION_LOCALIZATION,
        PKG_LOCALIZATION,
        PKG_ICON,
        PKG,
        PKG_USER_RATING_AGGREGATE,
        USER,
        REPOSITORY,
        NATURAL_LANGUAGE
    }

    private static HaikuDepot instance;

    private HaikuDepot() {}

    public static HaikuDepot getInstance() {
        if(instance == null) {
            instance = new HaikuDepot();
        }

        return instance;
    }

}
