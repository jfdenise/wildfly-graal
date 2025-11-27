
module HelloModule {
    exports hello;
    requires APIModule;
    uses api.Provider;
    requires org.jboss.modules;
    requires Mod1V1Module;
    requires Mod1V2Module;
}