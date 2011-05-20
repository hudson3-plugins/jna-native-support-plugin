// CHECKSTYLE:OFF

package hudson.util.jna;

import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;

@SuppressWarnings({
    "",
    "PMD"
})
public class Messages2 {

    private final static ResourceBundleHolder holder = ResourceBundleHolder.get(Messages2.class);

    /**
     * {0} needs to belong to group {1} to read /etc/shadow
     * 
     */
    public static String PAMSecurityRealm_BelongToGroup(Object arg1, Object arg2) {
        return holder.format("PAMSecurityRealm.BelongToGroup", arg1, arg2);
    }

    /**
     * {0} needs to belong to group {1} to read /etc/shadow
     * 
     */
    public static Localizable _PAMSecurityRealm_BelongToGroup(Object arg1, Object arg2) {
        return new Localizable(holder, "PAMSecurityRealm.BelongToGroup", arg1, arg2);
    }

    /**
     * Unix user/group database
     * 
     */
    public static String PAMSecurityRealm_DisplayName() {
        return holder.format("PAMSecurityRealm.DisplayName");
    }

    /**
     * Unix user/group database
     * 
     */
    public static Localizable _PAMSecurityRealm_DisplayName() {
        return new Localizable(holder, "PAMSecurityRealm.DisplayName");
    }
 
    /**
     * Success
     * 
     */
    public static String PAMSecurityRealm_Success() {
        return holder.format("PAMSecurityRealm.Success");
    }

    /**
     * Hudson needs to be able to read /etc/shadow
     * 
     */
    public static String PAMSecurityRealm_ReadPermission() {
        return holder.format("PAMSecurityRealm.ReadPermission");
    }

    /**
     * Hudson needs to be able to read /etc/shadow
     * 
     */
    public static Localizable _PAMSecurityRealm_ReadPermission() {
        return new Localizable(holder, "PAMSecurityRealm.ReadPermission");
    }

    /**
     * uid: {0}
     * 
     */
    public static String PAMSecurityRealm_Uid(Object arg1) {
        return holder.format("PAMSecurityRealm.Uid", arg1);
    }

    /**
     * uid: {0}
     * 
     */
    public static Localizable _PAMSecurityRealm_Uid(Object arg1) {
        return new Localizable(holder, "PAMSecurityRealm.Uid", arg1);
    }


    /**
     * Current User
     * 
     */
    public static String PAMSecurityRealm_CurrentUser() {
        return holder.format("PAMSecurityRealm.CurrentUser");
    }

    /**
     * Current User
     * 
     */
    public static Localizable _PAMSecurityRealm_CurrentUser() {
        return new Localizable(holder, "PAMSecurityRealm.CurrentUser");
    }


    /**
     * Either Hudson needs to run as {0} or {1} needs to belong to group {2} and ''chmod g+r /etc/shadow'' needs to be done to enable Hudson to read /etc/shadow
     * 
     */
    public static String PAMSecurityRealm_RunAsUserOrBelongToGroupAndChmod(Object arg1, Object arg2, Object arg3) {
        return holder.format("PAMSecurityRealm.RunAsUserOrBelongToGroupAndChmod", arg1, arg2, arg3);
    }

    /**
     * Either Hudson needs to run as {0} or {1} needs to belong to group {2} and ''chmod g+r /etc/shadow'' needs to be done to enable Hudson to read /etc/shadow
     * 
     */
    public static Localizable _PAMSecurityRealm_RunAsUserOrBelongToGroupAndChmod(Object arg1, Object arg2, Object arg3) {
        return new Localizable(holder, "PAMSecurityRealm.RunAsUserOrBelongToGroupAndChmod", arg1, arg2, arg3);
    }

    /**
     * User ''{0}''
     * 
     */
    public static String PAMSecurityRealm_User(Object arg1) {
        return holder.format("PAMSecurityRealm.User", arg1);
    }

    /**
     * User ''{0}''
     * 
     */
    public static Localizable _PAMSecurityRealm_User(Object arg1) {
        return new Localizable(holder, "PAMSecurityRealm.User", arg1);
    }

}
