package test.java;

import com.anymore.auto.AutoService;

@AutoService(value = Runnable.class, priority = 20, alias = "java")
public final class JavaModuleTask implements Runnable {
    @Override
    public void run() {
    }
}
