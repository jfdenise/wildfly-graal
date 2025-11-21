
module HelloModule {
    exports hello;
    requires APIModule;
    uses api.Provider;
}