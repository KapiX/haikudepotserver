/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.userrating.job;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.userrating.UserRatingDerivationService;
import org.haiku.haikudepotserver.userrating.UserRatingOrchestrationService;
import org.haiku.haikudepotserver.userrating.model.UserRatingDerivationJobSpecification;
import org.haiku.haikudepotserver.job.JobOrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;

/**
 * <p>This implementation of the {@link UserRatingDerivationService}
 * operates in the same runtime as the application and has no persistence or distributed behaviour.</p>
 */

public class UserRatingDerivationJobRunner
        extends AbstractJobRunner<UserRatingDerivationJobSpecification> {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserRatingDerivationJobRunner.class);

    @Resource
    UserRatingOrchestrationService userRatingOrchestrationService;

    public void run(JobOrchestrationService jobOrchestrationService, UserRatingDerivationJobSpecification job) {
        Preconditions.checkNotNull(job);

        if(job.appliesToAllPkgs()) {
            userRatingOrchestrationService.updateUserRatingDerivationsForAllPkgs();
        }
        else {
            userRatingOrchestrationService.updateUserRatingDerivation(job.getPkgName());
        }
    }

}
