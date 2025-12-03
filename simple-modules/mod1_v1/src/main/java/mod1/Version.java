
package mod1;

/**
 *
 * @author jdenise
 */
public class Version {
    static WarningLogger l;
    static {
        System.out.append("INIT VERSION CLASS" + Version.class.getClassLoader());
        l =  WarningLogger.WARNING_LOGGER;
    }
    public String getVersion() {
        l.warning("GETTING VERSION");
        return "Version_1";
    }
}
