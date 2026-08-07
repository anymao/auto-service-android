package test.external;

import com.anymore.auto.AutoService;

@AutoService(value = Runnable.class, priority = 40, alias = "bridge")
public final class BridgeTask implements Runnable {
    @Override
    public void run() {
    }
}
