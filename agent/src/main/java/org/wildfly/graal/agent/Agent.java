package org.wildfly.graal.agent;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class Agent {

    private static final String code2 = """
                   System.out.println("HELLO" + cl);
                   if (cl.toString().contains("ModuleClassLoader for Module ")) {
                      System.out.println("That is in a module");
                      org.jboss.ModuleClassLoader mcl = (org.jboss.ModuleClassLoader) cl;
                      java.nio.file.Path dir = java.nio.file.Paths.get("jboss-modules-store");
                                      java.nio.file.Files.createDirectories(dir);
                                      java.nio.file.Path file = dir.resolve(cl.getModule().getName());
                                      java.nio.file.Files.writeString(file, svc + "\n", java.nio.file.StandardOpenOption.APPEND, java.nio.file.StandardOpenOption.CREATE);
                   }                 
                  """;
    private static final String code = """
                                       if(loader != null) {
                   System.out.println("@@@@@@@@@@@@@AGENT, recording " + service.getName() + ", loader " + loader.toString());
                                       try {
                                            java.util.regex.Pattern ppp = java.util.regex.Pattern.compile("ModuleClassLoader for Module \\\"(.*)\\\" .*");
                                            java.util.regex.Matcher mmm = ppp.matcher(loader.toString());
                                            if(mmm.matches()) {
                                                synchronized(this) {
                                                String mmmoduleName = mmm.group(1);
                                                System.out.println("We have a module " + mmmoduleName);
                                                String rootDir = "jboss-modules-recorded-services/" + mmmoduleName;
                                                java.io.File dir = new java.io.File(rootDir);
                                                dir.mkdirs();
                                                java.io.FileOutputStream s = new java.io.FileOutputStream(rootDir +"/services.txt", true);
                                                java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(s);
                                                java.io.BufferedWriter writer = new java.io.BufferedWriter(w);
                                                java.lang.String content = service.getName() + java.lang.System.getProperty("line.separator");
                                                writer.write(content);
                                                writer.close();
                                                w.close();
                                                s.close();
                                                }
                                            } else {
                                               System.out.println("No match for " + service.getName());
                                               String rootDir = "jboss-modules-recorded-services/";
                                               java.io.File dir = new java.io.File(rootDir);
                                               dir.mkdirs();
                                               java.io.FileOutputStream s = new java.io.FileOutputStream(rootDir +"/_not-captured-services.txt", true);
                                               java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(s);
                                               java.io.BufferedWriter writer = new java.io.BufferedWriter(w);
                                               java.lang.String content = service.getName() + java.lang.System.getProperty("line.separator");
                                               writer.write(content);
                                               writer.close();
                                               w.close();
                                               s.close();               
                                            }
                                        } catch(Exception ex) {
                                            System.out.println(ex);                     
                                        }
                        }
                                                            
                  """;

    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader classLoader, String s, Class<?> aClass, ProtectionDomain protectionDomain, byte[] bytes) throws IllegalClassFormatException {
                try {
                    if (s.equals("java/util/ServiceLoader")) {
                        System.out.println("@@@@@@@@@@@@@AGENT, instrumenting ServiceLoader");
                        ClassPool cp = ClassPool.getDefault();
                        CtClass cc = cp.get("java.util.ServiceLoader");
                        CtMethod streamMethod = cc.getDeclaredMethod("stream");
                        streamMethod.insertAfter(code);
                        CtMethod iteratorMethod = cc.getDeclaredMethod("iterator");
                        iteratorMethod.insertAfter(code);
                        byte[] byteCode = cc.toBytecode();
                        cc.detach();
                        return byteCode;
                    }
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                return null;
            }
        });
    }

}
