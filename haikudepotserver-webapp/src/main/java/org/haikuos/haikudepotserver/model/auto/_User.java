package org.haikuos.haikudepotserver.model.auto;

import org.haikuos.haikudepotserver.model.support.AbstractDataObject;

/**
 * Class _User was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _User extends AbstractDataObject {

    public static final String ACTIVE_PROPERTY = "active";
    public static final String CAN_MANAGE_USERS_PROPERTY = "canManageUsers";
    public static final String IS_ROOT_PROPERTY = "isRoot";
    public static final String NICKNAME_PROPERTY = "nickname";
    public static final String PASSWORD_HASH_PROPERTY = "passwordHash";
    public static final String PASSWORD_SALT_PROPERTY = "passwordSalt";

    public static final String ID_PK_COLUMN = "id";

    public void setActive(Boolean active) {
        writeProperty(ACTIVE_PROPERTY, active);
    }
    public Boolean getActive() {
        return (Boolean)readProperty(ACTIVE_PROPERTY);
    }

    public void setCanManageUsers(Boolean canManageUsers) {
        writeProperty(CAN_MANAGE_USERS_PROPERTY, canManageUsers);
    }
    public Boolean getCanManageUsers() {
        return (Boolean)readProperty(CAN_MANAGE_USERS_PROPERTY);
    }

    public void setIsRoot(Boolean isRoot) {
        writeProperty(IS_ROOT_PROPERTY, isRoot);
    }
    public Boolean getIsRoot() {
        return (Boolean)readProperty(IS_ROOT_PROPERTY);
    }

    public void setNickname(String nickname) {
        writeProperty(NICKNAME_PROPERTY, nickname);
    }
    public String getNickname() {
        return (String)readProperty(NICKNAME_PROPERTY);
    }

    public void setPasswordHash(String passwordHash) {
        writeProperty(PASSWORD_HASH_PROPERTY, passwordHash);
    }
    public String getPasswordHash() {
        return (String)readProperty(PASSWORD_HASH_PROPERTY);
    }

    public void setPasswordSalt(String passwordSalt) {
        writeProperty(PASSWORD_SALT_PROPERTY, passwordSalt);
    }
    public String getPasswordSalt() {
        return (String)readProperty(PASSWORD_SALT_PROPERTY);
    }

    protected abstract void onPostAdd();

}
