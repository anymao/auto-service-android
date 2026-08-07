package test.sample;

import com.anymore.auto.AutoService;

@AutoService(value = Runnable.class, priority = 0, alias = "app")
public final class ServiceImpl implements Runnable {
    @Override
    public void run() {
    }
}
