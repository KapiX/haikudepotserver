/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haiku.haikudepotserver.api1.model.miscellaneous;

import java.util.List;

public class GetAllUserRatingStabilitiesResult {

    public List<UserRatingStability> userRatingStabilities;

    public GetAllUserRatingStabilitiesResult() {
    }

    public GetAllUserRatingStabilitiesResult(List<UserRatingStability> userRatingStabilities) {
        this.userRatingStabilities = userRatingStabilities;
    }

    public static class UserRatingStability {

        public String code;
        public String name;
        public Integer ordering;

        public UserRatingStability() {
        }

        public UserRatingStability(
                String code,
                String name,
                Integer ordering) {
            this.code = code;
            this.name = name;
            this.ordering = ordering;
        }
    }

}
