package com.example.main;

import java.lang.reflect.Constructor;

public class Main {

    public Main() {
        
    }
    public static void main(String[] args) throws Exception {
        String foo = System.getProperty("class");
        Class<?> clazz = Class.forName(foo);
        Constructor main = clazz.getDeclaredConstructor();
        System.out.println("MAIN " + main);
    }

}
