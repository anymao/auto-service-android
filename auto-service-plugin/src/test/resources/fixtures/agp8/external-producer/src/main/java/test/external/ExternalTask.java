package test.external;

import com.anymore.auto.AutoService;

@AutoService(value = Runnable.class, priority = 30, alias = "external")
public final class ExternalTask implements Runnable {
    @Override
    public void run() {
    }
}
