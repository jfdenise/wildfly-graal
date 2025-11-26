module ProviderModule {
    exports provider;
    requires APIModule;
    provides api.Provider
      with provider.ProviderImpl;
}