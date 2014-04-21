/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.api1.model.userrating.*;
import org.haikuos.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.security.AuthorizationService;
import org.haikuos.haikudepotserver.security.model.Permission;
import org.haikuos.haikudepotserver.support.VersionCoordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.UUID;

@Component
public class UserRatingApiImpl extends AbstractApiImpl implements UserRatingApi {

    protected static Logger logger = LoggerFactory.getLogger(UserApiImpl.class);

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    AuthorizationService authorizationService;

    /**
     * <p>Some of the result subclass from this abstract one.  This convenience method will full the
     * abstract result with the data from the user rating so that this code does not need to be
     * repeated across multiple API methods.</p>
     */

    private void fillAbstractGetUserRatingResult(UserRating userRating, AbstractGetUserRatingResult result) {
        Preconditions.checkNotNull(userRating);
        Preconditions.checkNotNull(result);

        result.active = userRating.getActive();
        result.code = userRating.getCode();
        result.naturalLanguageCode = userRating.getNaturalLanguage().getCode();
        result.userNickname = userRating.getUser().getNickname();
        result.rating = userRating.getRating();
        result.comment = userRating.getComment();
        result.modifyTimestamp = userRating.getModifyTimestamp().getTime();
        result.createTimestamp = userRating.getCreateTimestamp().getTime();
        result.pkgName = userRating.getPkgVersion().getPkg().getName();
        result.pkgVersionArchitectureCode = userRating.getPkgVersion().getArchitecture().getCode();
        result.pkgVersionMajor = userRating.getPkgVersion().getMajor();
        result.pkgVersionMinor = userRating.getPkgVersion().getMinor();
        result.pkgVersionMicro = userRating.getPkgVersion().getMicro();
        result.pkgVersionPreRelease = userRating.getPkgVersion().getPreRelease();
        result.pkgVersionRevision = userRating.getPkgVersion().getRevision();

        if(null!=userRating.getUserRatingStability()) {
            result.userRatingStabilityCode = userRating.getUserRatingStability().getCode();
        }
    }

    @Override
    public GetUserRatingResult getUserRating(GetUserRatingRequest request) throws ObjectNotFoundException {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.code));

        final ObjectContext context = serverRuntime.getContext();

        Optional<UserRating> userRatingOptional = UserRating.getByCode(context, request.code);

        if(!userRatingOptional.isPresent()) {
            throw new ObjectNotFoundException(UserRating.class.getSimpleName(), request.code);
        }

        GetUserRatingResult result = new GetUserRatingResult();
        fillAbstractGetUserRatingResult(userRatingOptional.get(), result);

        return result;
    }

    @Override
    public GetUserRatingByUserAndPkgVersionResult getUserRatingByUserAndPkgVersion(GetUserRatingByUserAndPkgVersionRequest request) throws ObjectNotFoundException {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgName), "the package name must be supplied");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.userNickname), "the user nickname must be supplied");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgVersionArchitectureCode), "the pkg version architecture code must be supplied");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgVersionMajor),"the package version major code must be supplied");

        final ObjectContext context = serverRuntime.getContext();

        Architecture architecture = getArchitecture(context, request.pkgVersionArchitectureCode);
        Optional<User> userOptional = User.getByNickname(context, request.userNickname);

        if(!userOptional.isPresent()) {
            throw new ObjectNotFoundException(User.class.getSimpleName(), request.userNickname);
        }

        Optional<Pkg> pkgOptional = Pkg.getByName(context, request.pkgName);

        if(!pkgOptional.isPresent()) {
            throw new ObjectNotFoundException(Pkg.class.getSimpleName(), request.pkgName);
        }

        VersionCoordinates versionCoordinates = new VersionCoordinates(
                request.pkgVersionMajor,
                request.pkgVersionMinor,
                request.pkgVersionMicro,
                request.pkgVersionPreRelease,
                request.pkgVersionRevision);

        Optional<PkgVersion> pkgVersionOptional = PkgVersion.getForPkg(
                context,
                pkgOptional.get(),
                architecture,
                versionCoordinates);

        if(!pkgVersionOptional.isPresent()) {
            throw new ObjectNotFoundException(
                    PkgVersion.class.getSimpleName(),
                    request.pkgName + "@" + versionCoordinates.toString());
        }

        Optional<UserRating> userRatingOptional = UserRating.getByUserAndPkgVersion(context, userOptional.get(), pkgVersionOptional.get());

        if(!userRatingOptional.isPresent()) {
            throw new ObjectNotFoundException(UserRating.class.getSimpleName(), "");
        }

        GetUserRatingByUserAndPkgVersionResult result = new GetUserRatingByUserAndPkgVersionResult();
        fillAbstractGetUserRatingResult(userRatingOptional.get(), result);

        return result;
    }


    @Override
    public CreateUserRatingResult createUserRating(CreateUserRatingRequest request) throws ObjectNotFoundException {
        Preconditions.checkNotNull(request);

        final ObjectContext context = serverRuntime.getContext();

        Optional<Pkg> pkgOptional = Pkg.getByName(context, request.pkgName);

        if(!pkgOptional.isPresent()) {
            throw new ObjectNotFoundException(Pkg.class.getSimpleName(), request.pkgName);
        }

        Architecture architecture = getArchitecture(context, request.pkgVersionArchitectureCode);
        NaturalLanguage naturalLanguage = getNaturalLanguage(context, request.naturalLanguageCode);

        Optional<User> userOptional = User.getByNickname(context, request.userNickname);

        if(!userOptional.isPresent()) {
            throw new ObjectNotFoundException(User.class.getSimpleName(), request.userNickname);
        }

        Optional<UserRatingStability> userRatingStabilityOptional = Optional.absent();

        if(null!=request.userRatingStabilityCode) {
            userRatingStabilityOptional = UserRatingStability.getByCode(context, request.userRatingStabilityCode);

            if(!userRatingStabilityOptional.isPresent()) {
                throw new ObjectNotFoundException(
                        UserRatingStability.class.getSimpleName(),
                        request.userRatingStabilityCode);
            }
        }

        // check authorization

        Optional<User> authenticatedUserOptional = tryObtainAuthenticatedUser(context);

        if(!authenticatedUserOptional.isPresent()) {
            logger.warn("only authenticated users are able to add user ratings");
            throw new AuthorizationFailureException();
        }

        if(!authenticatedUserOptional.get().getNickname().equals(userOptional.get().getNickname())) {
            logger.warn("it is not allowed to add a user rating for another user");
            throw new AuthorizationFailureException();
        }

        if(!authorizationService.check(
                context,
                authenticatedUserOptional.orNull(),
                pkgOptional.get(),
                Permission.PKG_CREATERATING)) {
            throw new AuthorizationFailureException();
        }

        // check the package version

        Optional<PkgVersion> pkgVersionOptional;

        switch(request.pkgVersionType) {
            case LATEST:
                pkgVersionOptional = PkgVersion.getLatestForPkg(
                        context,
                        pkgOptional.get(),
                        Collections.singletonList(architecture));
                break;

            default:
                throw new IllegalStateException("unsupported pkg version type; " + request.pkgVersionType.name());
        }

        if(!pkgVersionOptional.isPresent()) {
            throw new ObjectNotFoundException(PkgVersion.class.getSimpleName(), pkgOptional.get().getName()+"_"+request.pkgVersionType.name());
        }

        UserRating userRating = context.newObject(UserRating.class);
        userRating.setCode(UUID.randomUUID().toString());
        userRating.setUserRatingStability(userRatingStabilityOptional.orNull());
        userRating.setUser(userOptional.get());
        userRating.setComment(Strings.emptyToNull(request.comment.trim()));
        userRating.setPkgVersion(pkgVersionOptional.get());
        userRating.setNaturalLanguage(naturalLanguage);
        userRating.setRating(request.rating);

        context.commitChanges();

        logger.info(
                "did create user rating for user {} on package {}",
                userOptional.get().toString(),
                pkgOptional.get().toString());

        return new CreateUserRatingResult(userRating.getCode());

    }

    @Override
    public UpdateUserRatingResult updateUserRating(UpdateUserRatingRequest request) throws ObjectNotFoundException {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.code));
        Preconditions.checkNotNull(request.filter);

        final ObjectContext context = serverRuntime.getContext();

        Optional<UserRating> userRatingOptional = UserRating.getByCode(context, request.code);

        if(!userRatingOptional.isPresent()) {
            throw new ObjectNotFoundException(UserRating.class.getSimpleName(), request.code);
        }

        for(UpdateUserRatingRequest.Filter filter : request.filter) {

            switch(filter) {

                case COMMENT:
                    if(null!=request.comment) {
                        userRatingOptional.get().setComment(Strings.emptyToNull(request.comment.trim()));
                    }
                    else {
                        userRatingOptional.get().setComment(null);
                    }
                    break;

                case NATURALLANGUAGE:
                    NaturalLanguage naturalLanguage = getNaturalLanguage(context, request.naturalLanguageCode);
                    userRatingOptional.get().setNaturalLanguage(naturalLanguage);
                    break;

                case RATING:
                    userRatingOptional.get().setRating(request.rating);
                    break;

                case USERRATINGSTABILITY:
                    Optional<UserRatingStability> userRatingStabilityOptional = UserRatingStability.getByCode(
                            context,
                            request.userRatingStabilityCode);

                    if(!userRatingStabilityOptional.isPresent()) {
                        throw new ObjectNotFoundException(
                                UserRatingStability.class.getSimpleName(),
                                request.userRatingStabilityCode);
                    }

                    userRatingOptional.get().setUserRatingStability(userRatingStabilityOptional.get());
                    break;

                default:
                    throw new IllegalStateException("the filter; "+filter.name()+" is not handled");

            }

        }

        logger.info(
                "did update user rating for user {} on package {}",
                userRatingOptional.get().getUser().toString(),
                userRatingOptional.get().getPkgVersion().getPkg().toString());

        context.commitChanges();

        return new UpdateUserRatingResult();
    }

}