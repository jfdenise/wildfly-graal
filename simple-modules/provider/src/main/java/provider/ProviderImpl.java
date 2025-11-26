
package provider;

import api.Provider;

/**
 *
 * @author jdenise
 */
public class ProviderImpl implements Provider {
    @Override
    public String getIt() {
        return "foo";
    }
}
