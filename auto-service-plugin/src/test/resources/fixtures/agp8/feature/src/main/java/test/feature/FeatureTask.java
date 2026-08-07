package feature;

import com.anymore.auto.AutoService;

@AutoService(value = Runnable.class, alias = "feature", priority = -10)
public final class FeatureTask implements Runnable {
    @Override
    public void run() {
    }
}
