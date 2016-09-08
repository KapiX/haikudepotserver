/*
 * Copyright 2013-2014 Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Uninterruptibles;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api1.model.user.*;
import org.haiku.haikudepotserver.api1.support.*;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.passwordreset.PasswordResetOrchestrationService;
import org.haiku.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haiku.haikudepotserver.security.AuthenticationService;
import org.haiku.haikudepotserver.security.AuthorizationService;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.user.UserOrchestrationService;
import org.haiku.haikudepotserver.user.model.LdapSynchronizeUsersJobSpecification;
import org.haiku.haikudepotserver.user.model.UserSearchSpecification;
import org.haiku.haikudepotserver.userrating.model.UserRatingDerivationJobSpecification;
import org.haiku.haikudepotserver.captcha.CaptchaService;
import org.haiku.haikudepotserver.job.JobOrchestrationService;
import org.haiku.haikudepotserver.userrating.UserRatingOrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@AutoJsonRpcServiceImpl
public class UserApiImpl extends AbstractApiImpl implements UserApi {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserApiImpl.class);

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private AuthorizationService authorizationService;

    @Resource
    private CaptchaService captchaService;

    @Resource
    private AuthenticationService authenticationService;

    @Resource
    private UserOrchestrationService userOrchestrationService;

    @Resource
    private UserRatingOrchestrationService userRatingOrchestrationService;

    @Resource
    private PasswordResetOrchestrationService passwordResetOrchestrationService;

    @Resource
    private JobOrchestrationService jobOrchestrationService;

    @Override
    public SynchronizeUsersResult synchronizeUsers(SynchronizeUsersRequest synchronizeUsersRequest) {
       Preconditions.checkNotNull(synchronizeUsersRequest);

        final ObjectContext context = serverRuntime.getContext();

        if(!authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orElse(null),
                null,
                Permission.USER_SYNCHRONIZE)) {
            throw new AuthorizationFailureException();
        }

        jobOrchestrationService.submit(
                new LdapSynchronizeUsersJobSpecification(),
                JobOrchestrationService.CoalesceMode.QUEUED);

        return new SynchronizeUsersResult();
    }

    @Override
    public UpdateUserResult updateUser(UpdateUserRequest updateUserRequest) throws ObjectNotFoundException {

        Preconditions.checkNotNull(updateUserRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(updateUserRequest.nickname));
        Preconditions.checkNotNull(updateUserRequest.filter);

        final ObjectContext context = serverRuntime.getContext();
        User authUser = obtainAuthenticatedUser(context);
        boolean activeDidChange = false;

        Optional<User> user = User.getByNickname(context, updateUserRequest.nickname);

        if (!user.isPresent()) {
            throw new ObjectNotFoundException(User.class.getSimpleName(), User.NICKNAME_PROPERTY);
        }

        if (!authorizationService.check(context, authUser, user.get(), Permission.USER_EDIT)) {
            throw new AuthorizationFailureException();
        }

        for (UpdateUserRequest.Filter filter : updateUserRequest.filter) {

            switch (filter) {

                case NATURALLANGUAGE:

                    if(Strings.isNullOrEmpty(updateUserRequest.naturalLanguageCode)) {
                        throw new IllegalStateException("the natural language code is required to update the natural language on a user");
                    }

                    Optional<NaturalLanguage> naturalLanguageOptional = NaturalLanguage.getByCode(
                            context,
                            updateUserRequest.naturalLanguageCode);

                    if(!naturalLanguageOptional.isPresent()) {
                        throw new ObjectNotFoundException(NaturalLanguage.class.getSimpleName(), updateUserRequest.naturalLanguageCode);
                    }

                    user.get().setNaturalLanguage(naturalLanguageOptional.get());

                    LOGGER.info("will update the natural language on the user {} to {}", user.get().toString(), naturalLanguageOptional.get().toString());

                    break;

                case EMAIL:
                    user.get().setEmail(updateUserRequest.email);
                    break;

                case ACTIVE:
                    if(null==updateUserRequest.active) {
                        throw new IllegalStateException("the 'active' attribute is required to configure active on the user.");
                    }

                    activeDidChange = user.get().getActive() != updateUserRequest.active;
                    user.get().setActive(updateUserRequest.active);

                    break;

                default:
                    throw new IllegalStateException("unknown filter in edit user; " + filter.name());

            }

        }

        if(context.hasChanges()) {
            context.commitChanges();
            LOGGER.info("did update the user {}", user.get().toString());

            // if a user is made active or inactive will have some impact on the user-ratings.

            if(activeDidChange) {
                List<String> pkgNames = userRatingOrchestrationService.pkgNamesEffectedByUserActiveStateChange(
                        context, user.get());

                LOGGER.info(
                        "will update user rating derivation for {} packages owing to active state change on user {}",
                        pkgNames.size(),
                        user.get().toString());

                for(String pkgName : pkgNames) {
                    jobOrchestrationService.submit(
                            new UserRatingDerivationJobSpecification(pkgName),
                            JobOrchestrationService.CoalesceMode.QUEUED);
                }
            }

        }
        else {
            LOGGER.info("no changes in updating the user {}", user.get().toString());
        }

        return new UpdateUserResult();
    }

    @Override
    public CreateUserResult createUser(CreateUserRequest createUserRequest) throws ObjectNotFoundException {

        Preconditions.checkNotNull(createUserRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(createUserRequest.nickname));
        Preconditions.checkState(!Strings.isNullOrEmpty(createUserRequest.passwordClear));
        Preconditions.checkState(!Strings.isNullOrEmpty(createUserRequest.captchaToken));
        Preconditions.checkState(!Strings.isNullOrEmpty(createUserRequest.captchaResponse),"a capture response is required to create a user");
        Preconditions.checkState(!Strings.isNullOrEmpty(createUserRequest.naturalLanguageCode));

        if(!authenticationService.validatePassword(createUserRequest.passwordClear)) {
            throw new ValidationException(new ValidationFailure("passwordClear", "invalid"));
        }

        // check the supplied catcha matches the token.

        if(!captchaService.verify(createUserRequest.captchaToken, createUserRequest.captchaResponse)) {
            throw new CaptchaBadResponseException();
        }

        // we need to check the nickname even before we create the user because we have to
        // check for uniqueness of the nickname across all of the users.

        if(Strings.isNullOrEmpty(createUserRequest.nickname)) {
            throw new ValidationException(
                    new ValidationFailure(
                            User.NICKNAME_PROPERTY,"required")
            );
        }

        final ObjectContext context = serverRuntime.getContext();

        //need to check that the nickname is not already in use.

        if(User.getByNickname(context,createUserRequest.nickname).isPresent()) {
            throw new ValidationException(
                    new ValidationFailure(
                            User.NICKNAME_PROPERTY,"notunique")
            );
        }

        Optional<NaturalLanguage> naturalLanguageOptional = NaturalLanguage.getByCode(
                context,
                createUserRequest.naturalLanguageCode);

        if(!naturalLanguageOptional.isPresent()) {
            throw new ObjectNotFoundException(
                    NaturalLanguage.class.getSimpleName(),
                    createUserRequest.naturalLanguageCode);
        }

        User user = context.newObject(User.class);
        user.setNaturalLanguage(naturalLanguageOptional.get());
        user.setNickname(createUserRequest.nickname);
        user.setPasswordSalt(); // random
        user.setEmail(createUserRequest.email);
        user.setPasswordHash(authenticationService.hashPassword(user, createUserRequest.passwordClear));
        context.commitChanges();

        LOGGER.info("data create user; {}", user.getNickname());

        return new CreateUserResult();
    }

    @Override
    public GetUserResult getUser(GetUserRequest getUserRequest) throws ObjectNotFoundException {
        Preconditions.checkNotNull(getUserRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(getUserRequest.nickname));

        final ObjectContext context = serverRuntime.getContext();
        User authUser = obtainAuthenticatedUser(context);

        Optional<User> user = User.getByNickname(context, getUserRequest.nickname);

        if(!user.isPresent()) {
            throw new ObjectNotFoundException(User.class.getSimpleName(), User.NICKNAME_PROPERTY);
        }

        if(!authorizationService.check(context, authUser, user.get(), Permission.USER_VIEW)) {
            throw new AuthorizationFailureException();
        }

        GetUserResult result = new GetUserResult();
        result.nickname = user.get().getNickname();
        result.email = user.get().getEmail();
        result.isRoot = user.get().getIsRoot();
        result.active = user.get().getActive();
        result.naturalLanguageCode = user.get().getNaturalLanguage().getCode();
        result.createTimestamp = user.get().getCreateTimestamp().getTime();
        result.modifyTimestamp = user.get().getModifyTimestamp().getTime();
        return result;
    }

    // TODO; some sort of brute-force checking here; too many authentication requests in a short period; go into lock-down?

    @Override
    public AuthenticateUserResult authenticateUser(AuthenticateUserRequest authenticateUserRequest) {
        Preconditions.checkNotNull(authenticateUserRequest);
        AuthenticateUserResult authenticateUserResult = new AuthenticateUserResult();
        authenticateUserResult.token = null;

        if (null != authenticateUserRequest.nickname) {
            authenticateUserRequest.nickname = authenticateUserRequest.nickname.trim();
        }

        if (null != authenticateUserRequest.passwordClear) {
            authenticateUserRequest.passwordClear = authenticateUserRequest.passwordClear.trim();
        }

        Optional<ObjectId> userOidOptional = authenticationService.authenticateByNicknameAndPassword(
                authenticateUserRequest.nickname,
                authenticateUserRequest.passwordClear);

        if(!userOidOptional.isPresent()) {

            // if the authentication has failed then best to sleep for a moment
            // to make brute forcing a bit more tricky.
            // TODO; this will eat threads; any other way to do it?

            Uninterruptibles.sleepUninterruptibly(5,TimeUnit.SECONDS);
        }
        else {
            ObjectContext context = serverRuntime.getContext();
            User user = User.getByObjectId(context, userOidOptional.get());
            authenticateUserResult.token = authenticationService.generateToken(user);
        }

        return authenticateUserResult;
    }

    @Override
    public RenewTokenResult renewToken(RenewTokenRequest renewTokenRequest) {
        Preconditions.checkNotNull(renewTokenRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(renewTokenRequest.token));
        RenewTokenResult result = new RenewTokenResult();

        Optional<ObjectId> userOidOptional = authenticationService.authenticateByToken(renewTokenRequest.token);

        if(userOidOptional.isPresent()) {
            ObjectContext context = serverRuntime.getContext();
            User user = User.getByObjectId(context, userOidOptional.get());
            result.token = authenticationService.generateToken(user);
            LOGGER.debug("did renew token for user; {}", user.toString());
        }
        else {
            LOGGER.info("unable to renew token");
        }

        return result;
    }


    @Override
    public ChangePasswordResult changePassword(
            ChangePasswordRequest changePasswordRequest)
            throws ObjectNotFoundException, AuthorizationFailureException, ValidationException {

        Preconditions.checkNotNull(changePasswordRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(changePasswordRequest.nickname));
        Preconditions.checkState(!Strings.isNullOrEmpty(changePasswordRequest.newPasswordClear));

        // get the logged in and target user.

        final ObjectContext context = serverRuntime.getContext();

        User authUser = obtainAuthenticatedUser(context);

        Optional<User> targetUserOptional = User.getByNickname(context, changePasswordRequest.nickname);

        if(!targetUserOptional.isPresent()) {
            throw new ObjectNotFoundException(User.class.getSimpleName(), changePasswordRequest.nickname);
        }

        User targetUser = targetUserOptional.get();

        if(!authorizationService.check(context, authUser, targetUserOptional.get(), Permission.USER_CHANGEPASSWORD)) {
            LOGGER.info("the logged in user {} is not allowed to change the password of another user {}", authUser.getNickname(), changePasswordRequest.nickname);
            throw new AuthorizationFailureException();
        }

        // if the user is changing their own password then they need to know their existing password
        // first.

        if(!authenticationService.validatePassword(changePasswordRequest.newPasswordClear)) {
            throw new ValidationException(new ValidationFailure("passwordClear", "invalid"));
        }

        // we need to make sure that the captcha is valid.

        if(Strings.isNullOrEmpty(changePasswordRequest.captchaToken)) {
            throw new IllegalStateException("the captcha token must be supplied to change the password");
        }

        if(Strings.isNullOrEmpty(changePasswordRequest.captchaResponse)) {
            throw new IllegalStateException("the captcha response must be supplied to change the password");
        }

        if(!captchaService.verify(changePasswordRequest.captchaToken, changePasswordRequest.captchaResponse)) {
            throw new CaptchaBadResponseException();
        }

        // we need to make sure that the old and new passwords match-up.

        if(targetUser.getNickname().equals(authUser.getNickname())) {
            if (Strings.isNullOrEmpty(changePasswordRequest.oldPasswordClear)) {
                throw new IllegalStateException("the old password clear is required to change the password of a user unless the logged in user is root.");
            }

            if (!authenticationService.authenticateByNicknameAndPassword(
                    changePasswordRequest.nickname,
                    changePasswordRequest.oldPasswordClear).isPresent()) {

                // if the old password does not match to the user then we should present this
                // as a validation failure rather than an authorization failure.

                LOGGER.info("the supplied old password is invalid for the user {}", changePasswordRequest.nickname);

                throw new ValidationException(new ValidationFailure("oldPasswordClear", "mismatched"));
            }
        }

        targetUser.setPasswordHash(authenticationService.hashPassword(targetUser, changePasswordRequest.newPasswordClear));
        context.commitChanges();
        LOGGER.info("did change password for user {}", changePasswordRequest.nickname);

        return new ChangePasswordResult();
    }

    @Override
    public SearchUsersResult searchUsers(SearchUsersRequest searchUsersRequest) {
        Preconditions.checkNotNull(searchUsersRequest);

        final ObjectContext context = serverRuntime.getContext();

        if(!authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orElse(null),
                null,
                Permission.USER_LIST)) {
            throw new AuthorizationFailureException();
        }

        UserSearchSpecification specification = new UserSearchSpecification();
        String exp = searchUsersRequest.expression;

        if(null!=exp) {
            exp = Strings.emptyToNull(exp.trim().toLowerCase());
        }

        specification.setExpression(exp);

        if(null!= searchUsersRequest.expressionType) {
            specification.setExpressionType(
                    PkgSearchSpecification.ExpressionType.valueOf(searchUsersRequest.expressionType.name()));
        }

        specification.setLimit(searchUsersRequest.limit);
        specification.setOffset(searchUsersRequest.offset);
        specification.setIncludeInactive(null!= searchUsersRequest.includeInactive && searchUsersRequest.includeInactive);

        SearchUsersResult result = new SearchUsersResult();

        result.total = userOrchestrationService.total(context,specification);
        result.items = Collections.emptyList();

        if(0 != result.total) {
            List<User> searchedUsers = userOrchestrationService.search(context,specification);

            result.items = searchedUsers.stream().map(u -> {
                SearchUsersResult.User resultUser = new SearchUsersResult.User();
                resultUser.active = u.getActive();
                resultUser.nickname = u.getNickname();
                return resultUser;
            }).collect(Collectors.toList());
        }

        return result;

    }

    @Override
    public InitiatePasswordResetResult initiatePasswordReset(InitiatePasswordResetRequest initiatePasswordResetRequest) {
        Preconditions.checkNotNull(initiatePasswordResetRequest);

        if(!captchaService.verify(initiatePasswordResetRequest.captchaToken, initiatePasswordResetRequest.captchaResponse)) {
            throw new CaptchaBadResponseException();
        }

        try {
            passwordResetOrchestrationService.initiate(initiatePasswordResetRequest.email);
        }
        catch(Throwable th) {
            LOGGER.error("unable to initiate password reset", th);
        }

        return new InitiatePasswordResetResult();
    }

    @Override
    public CompletePasswordResetResult completePasswordReset(CompletePasswordResetRequest completePasswordResetRequest) {
        Preconditions.checkNotNull(completePasswordResetRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(completePasswordResetRequest.captchaToken), "a capture token is required");
        Preconditions.checkState(!Strings.isNullOrEmpty(completePasswordResetRequest.token), "a token is required to facilitate the reset of the password");
        Preconditions.checkState(!Strings.isNullOrEmpty(completePasswordResetRequest.passwordClear), "a new password is required to reset the password");

        if(!captchaService.verify(completePasswordResetRequest.captchaToken, completePasswordResetRequest.captchaResponse)) {
            throw new CaptchaBadResponseException();
        }

        try {
            passwordResetOrchestrationService.complete(
                    completePasswordResetRequest.token,
                    completePasswordResetRequest.passwordClear);
        }
        catch(Throwable th) {
            LOGGER.error("unable to complete password reset", th);
        }

        return new CompletePasswordResetResult();
    }

}
