/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api1.model.authorization.*;
import org.haiku.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.api1.support.ValidationException;
import org.haiku.haikudepotserver.api1.support.ValidationFailure;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.security.model.AuthorizationPkgRuleSearchSpecification;
import org.haiku.haikudepotserver.security.model.AuthorizationPkgRuleService;
import org.haiku.haikudepotserver.security.model.AuthorizationService;
import org.haiku.haikudepotserver.security.model.TargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@AutoJsonRpcServiceImpl(additionalPaths = "/api/v1/authorization") // TODO; legacy path - remove
public class AuthorizationApiImpl extends AbstractApiImpl implements AuthorizationApi {

    protected static Logger LOGGER = LoggerFactory.getLogger(AuthorizationApiImpl.class);

    private final ServerRuntime serverRuntime;
    private final AuthorizationService authorizationService;
    private final AuthorizationPkgRuleService authorizationPkgRulesService;

    public AuthorizationApiImpl(
            ServerRuntime serverRuntime,
            AuthorizationService authorizationService,
            AuthorizationPkgRuleService authorizationPkgRulesService
    ) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.authorizationService = Preconditions.checkNotNull(authorizationService);
        this.authorizationPkgRulesService = Preconditions.checkNotNull(authorizationPkgRulesService);
    }

    // -------------------------------
    // HELPERS

    private org.haiku.haikudepotserver.dataobjects.Permission ensurePermission(ObjectContext context, String code) throws ObjectNotFoundException {
        return org.haiku.haikudepotserver.dataobjects.Permission.getByCode(context, code)
                .orElseThrow(() -> new ObjectNotFoundException(
                        org.haiku.haikudepotserver.dataobjects.Permission.class.getSimpleName(),
                        code));
    }

    private Pkg ensurePkg(ObjectContext context, String name) throws ObjectNotFoundException {
        return Pkg.tryGetByName(context, name)
                .orElseThrow(() -> new ObjectNotFoundException(Pkg.class.getSimpleName(), name));
    }

    private User ensureUser(ObjectContext context, String nickname) throws ObjectNotFoundException {
        return User.tryGetByNickname(context, nickname)
                .orElseThrow(() -> new ObjectNotFoundException(User.class.getSimpleName(), nickname));
    }

    /**
     * <P>Checks that the currently authenticated user is able to manipulate the authorization configuration
     * of the system.</P>
     */

    private void ensureCanAuthorizationManipulate() {
        ObjectContext context = serverRuntime.newContext();
        User authUser = obtainAuthenticatedUser(context);

        if(!authorizationService.check(context, authUser, null, org.haiku.haikudepotserver.security.model.Permission.AUTHORIZATION_CONFIGURE)) {
            throw new AuthorizationFailureException();
        }
    }

    // -------------------------------
    // API

    @Override
    public CheckAuthorizationResult checkAuthorization(CheckAuthorizationRequest deriveAuthorizationRequest) {

        Preconditions.checkNotNull(deriveAuthorizationRequest);
        Preconditions.checkNotNull(deriveAuthorizationRequest.targetAndPermissions);

        final ObjectContext context = serverRuntime.newContext();
        CheckAuthorizationResult result = new CheckAuthorizationResult();
        result.targetAndPermissions = new ArrayList<>();

        for(CheckAuthorizationRequest.AuthorizationTargetAndPermission targetAndPermission : deriveAuthorizationRequest.targetAndPermissions) {

            CheckAuthorizationResult.AuthorizationTargetAndPermission authorizationTargetAndPermission = new CheckAuthorizationResult.AuthorizationTargetAndPermission();

            authorizationTargetAndPermission.permissionCode = targetAndPermission.permissionCode;
            authorizationTargetAndPermission.targetIdentifier = targetAndPermission.targetIdentifier;
            authorizationTargetAndPermission.targetType = targetAndPermission.targetType;

            authorizationTargetAndPermission.authorized = authorizationService.check(
                    context,
                    tryObtainAuthenticatedUser(context).orElse(null),
                    null!=targetAndPermission.targetType ? TargetType.valueOf(targetAndPermission.targetType.name()) : null,
                    targetAndPermission.targetIdentifier,
                    org.haiku.haikudepotserver.security.model.Permission.valueOf(targetAndPermission.permissionCode));

            result.targetAndPermissions.add(authorizationTargetAndPermission);
        }

        return result;
    }

    @Override
    public CreateAuthorizationPkgRuleResult createAuthorizationPkgRule(CreateAuthorizationPkgRuleRequest request) throws ObjectNotFoundException,AuthorizationRuleConflictException {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.permissionCode), "the permission code is required");
        Preconditions.checkState(org.haiku.haikudepotserver.security.model.Permission.valueOf(request.permissionCode.toUpperCase()).getRequiredTargetType() == TargetType.PKG,"the permission should have a target type of; " + TargetType.PKG);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.userNickname),"the user nickname must be supplied");

        ensureCanAuthorizationManipulate();
        ObjectContext context = serverRuntime.newContext();
        org.haiku.haikudepotserver.dataobjects.Permission permission = ensurePermission(context, request.permissionCode);
        User user = ensureUser(context, request.userNickname);

        if(user.getIsRoot()) {
            throw new ValidationException(new ValidationFailure("user", "root"));
        }

        Pkg pkg = null;

        if(null!=request.pkgName) {
            pkg = ensurePkg(context, request.pkgName);
        }

        // now we need to check to make sure that the newly added rule does not conflict with an existing
        // rule.  If this is the case then exception.

        if(authorizationPkgRulesService.wouldConflict(context,user,permission,pkg)) {
            throw new AuthorizationRuleConflictException();
        }

        authorizationPkgRulesService.create(
                context, user,
                permission,
                pkg);

        context.commitChanges();

        return new CreateAuthorizationPkgRuleResult();
    }

    @Override
    public RemoveAuthorizationPkgRuleResult removeAuthorizationPkgRule(RemoveAuthorizationPkgRuleRequest request) throws ObjectNotFoundException {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.permissionCode), "the permission code is required");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.userNickname),"the user nickname is required");

        ensureCanAuthorizationManipulate();
        ObjectContext context = serverRuntime.newContext();
        org.haiku.haikudepotserver.dataobjects.Permission permission = ensurePermission(context, request.permissionCode);
        User user = null;

        if(null!=request.userNickname) {
            user = ensureUser(context, request.userNickname);
        }

        Pkg pkg = null;

        if(!Strings.isNullOrEmpty(request.pkgName)) {
            pkg = ensurePkg(context, request.pkgName);
        }

        authorizationPkgRulesService.remove(
                context,
                user,
                permission,
                pkg);

        context.commitChanges();

        return new RemoveAuthorizationPkgRuleResult();
    }

    @Override
    public SearchAuthorizationPkgRulesResult searchAuthorizationPkgRules(SearchAuthorizationPkgRulesRequest request) throws ObjectNotFoundException {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(null==request.limit || request.limit > 0);
        Preconditions.checkState(null!=request.offset && request.offset >= 0);

        ensureCanAuthorizationManipulate();

        final ObjectContext context = serverRuntime.newContext();

        AuthorizationPkgRuleSearchSpecification specification = new AuthorizationPkgRuleSearchSpecification();

        specification.setLimit(request.limit);
        specification.setOffset(request.offset);
        specification.setIncludeInactive(false);

        if(!Strings.isNullOrEmpty(request.userNickname)) {
            specification.setUser(ensureUser(context, request.userNickname));
        }

        if(null!=request.permissionCodes) {
            List<org.haiku.haikudepotserver.dataobjects.Permission> permissions = new ArrayList<>();

            for(int i=0;i<request.permissionCodes.size();i++) {
                permissions.add(ensurePermission(context, request.permissionCodes.get(i)));
            }

            specification.setPermissions(permissions);
        }

        if(!Strings.isNullOrEmpty(request.pkgName)) {
            specification.setPkg(ensurePkg(context, request.pkgName));
        }

        SearchAuthorizationPkgRulesResult result = new SearchAuthorizationPkgRulesResult();
        result.total = authorizationPkgRulesService.total(context, specification);
        result.items = authorizationPkgRulesService.search(context, specification)
                .stream()
                .map(r -> {
                            SearchAuthorizationPkgRulesResult.Rule rule = new SearchAuthorizationPkgRulesResult.Rule();
                            rule.permissionCode = r.getPermission().getCode();
                            rule.userNickname = r.getUser().getNickname();
                            rule.pkgName = null != r.getPkg() ? r.getPkg().getName() : null;
                            return rule;
                        }
                )
                .collect(Collectors.toList());

        return result;
    }

}
