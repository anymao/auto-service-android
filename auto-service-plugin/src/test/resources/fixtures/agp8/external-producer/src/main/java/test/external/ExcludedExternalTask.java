package test.external;

import com.anymore.auto.AutoService;

@AutoService(value = Runnable.class, priority = 35, alias = "excluded")
public final class ExcludedExternalTask implements Runnable {
    @Override
    public void run() {
    }
}
